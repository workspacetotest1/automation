/*
 * Copyright (c) 2023. Baidu, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package com.baidu.bifromq.baserpc;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static java.util.Collections.unmodifiableMap;

import com.baidu.bifromq.basehlc.HLC;
import com.google.protobuf.Message;
import com.google.protobuf.UnknownFieldSet;
import io.grpc.MethodDescriptor;
import io.grpc.ServiceDescriptor;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import javax.annotation.Nullable;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.NoArgsConstructor;

public final class BluePrint {
    private static final int PIGGYBACK_FIELD_ID = Short.MAX_VALUE;

    public interface Unary {
        // a marker interface
    }

    public interface PipelineUnary {
        // a marker interface
    }

    public interface Streaming {
        // a marker interface
    }

    public interface DDBalanced {
        // direct designated
        // a marker interface
    }

    public interface WRRBalanced {
        // a marker interface
    }

    public interface WRBalanced {
        // a marker interface
    }

    public interface WCHBalanced {
    }

    public interface WCHBalancedReq<ReqT> extends WCHBalanced {
        // marker interface
        String hashKey(ReqT req);
    }

    public abstract static class MethodSemantic<ReqT> {
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DDUnaryMethod extends MethodSemantic implements Unary, DDBalanced {
        public static final DDUnaryMethod INSTANCE = new DDUnaryMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WRRUnaryMethod extends MethodSemantic implements Unary, WRRBalanced {
        public static final WRRUnaryMethod INSTANCE = new WRRUnaryMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WRUnaryMethod extends MethodSemantic implements Unary, WRBalanced {
        public static final WRUnaryMethod INSTANCE = new WRUnaryMethod();
    }

    @Builder
    public static final class WCHUnaryMethod<ReqT> extends MethodSemantic implements Unary, WCHBalancedReq<ReqT> {
        private final Function<ReqT, String> keyHashFunc;

        public String hashKey(ReqT req) {
            return keyHashFunc.apply(req);
        }
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DDPipelineUnaryMethod extends MethodSemantic implements PipelineUnary, DDBalanced {
        public static final DDPipelineUnaryMethod INSTANCE = new DDPipelineUnaryMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WRRPipelineUnaryMethod extends MethodSemantic implements PipelineUnary, WRRBalanced {
        public static final WRRPipelineUnaryMethod INSTANCE = new WRRPipelineUnaryMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WRPipelineUnaryMethod extends MethodSemantic implements PipelineUnary, WRBalanced {
        public static final WRPipelineUnaryMethod INSTANCE = new WRPipelineUnaryMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WCHPipelineUnaryMethod extends MethodSemantic implements PipelineUnary, WCHBalanced {
        public static final WCHPipelineUnaryMethod INSTANCE = new WCHPipelineUnaryMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class DDStreamingMethod extends MethodSemantic implements Streaming, DDBalanced {
        public static final DDStreamingMethod INSTANCE = new DDStreamingMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WRRStreamingMethod extends MethodSemantic implements Streaming, WRRBalanced {
        public static final WRRStreamingMethod INSTANCE = new WRRStreamingMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WRStreamingMethod extends MethodSemantic implements Streaming, WRBalanced {
        public static final WRStreamingMethod INSTANCE = new WRStreamingMethod();
    }

    @NoArgsConstructor(access = AccessLevel.PRIVATE)
    public static final class WCHStreamingMethod extends MethodSemantic implements Streaming, WCHBalanced {
        public static final WCHStreamingMethod INSTANCE = new WCHStreamingMethod();
    }

    private final ServiceDescriptor serviceDescriptor;
    private final Map<String, MethodSemantic> methodSemantics;
    private final Map<String, MethodDescriptor> methods;
    private final Map<String, MethodDescriptor> wrappedMethods;

    private BluePrint(
        ServiceDescriptor serviceDescriptor,
        Map<String, MethodSemantic> methodSemantics,
        Map<String, MethodDescriptor> methods,
        Map<String, MethodDescriptor> wrappedMethods) {
        this.serviceDescriptor = serviceDescriptor;
        this.methodSemantics = methodSemantics;
        this.methods = methods;
        this.wrappedMethods = wrappedMethods;
        if (!serviceDescriptor.getMethods().containsAll(methods.values())) {
            throw new RuntimeException("Some method is not defined in the supplied service descriptor");
        }
        for (String methodName : methodSemantics.keySet()) {
            MethodDescriptor methodDesc = wrappedMethods.get(methodName);
            MethodSemantic semantic = methodSemantics.get(methodName);
            switch (methodDesc.getType()) {
                case UNARY:
                    if (!(semantic instanceof Unary)) {
                        // unary rpc could not be configured as pipelining method
                        throw new RuntimeException("Wrong semantic for Unary rpc");
                    }
                    break;
                case BIDI_STREAMING:
                    if (!(semantic instanceof PipelineUnary) && !(semantic instanceof Streaming)) {
                        // bidi streaming rpc could only be configured as either request/response pipeline
                        // or wrr/wch streaming method
                        throw new RuntimeException("Wrong semantic configured for bidi streaming rpc");
                    }
                    break;
                default:
                    throw new RuntimeException("Unknown method type: " + methodDesc.getType());
            }
        }
    }

    public ServiceDescriptor serviceDescriptor() {
        return serviceDescriptor;
    }

    public Set<String> allMethods() {
        return wrappedMethods.keySet();
    }

    public MethodSemantic semantic(String fullMethodName) {
        return methodSemantics.get(fullMethodName);
    }

    public MethodDescriptor methodDesc(String fullMethodName, boolean inProc) {
        if (inProc) {
            return methods.get(fullMethodName);
        }
        return wrappedMethods.get(fullMethodName);
    }

    public static BluePrintBuilder builder() {
        return new BluePrintBuilder();
    }

    public static class BluePrintBuilder {
        private ServiceDescriptor serviceDescriptor;
        private ArrayList<MethodDescriptor> methods;
        private ArrayList<MethodDescriptor> wrappedMethods;
        private ArrayList<MethodSemantic> methodSemantics;

        BluePrintBuilder() {
        }

        public BluePrintBuilder serviceDescriptor(ServiceDescriptor serviceDescriptor) {
            this.serviceDescriptor = serviceDescriptor;
            return this;
        }

        public <ReqT, RespT> BluePrintBuilder methodSemantic(
            MethodDescriptor<ReqT, RespT> methodSemanticKey, MethodSemantic<ReqT> methodSemanticValue) {
            if (this.methods == null) {
                this.methods = new ArrayList<>();
                this.wrappedMethods = new ArrayList<>();
                this.methodSemantics = new ArrayList<>();
            }
            this.methodSemantics.add(methodSemanticValue);
            this.methods.add(methodSemanticKey);
            this.wrappedMethods.add(methodSemanticKey.toBuilder()
                .setRequestMarshaller(withHLC((MethodDescriptor.PrototypeMarshaller<ReqT>)
                    methodSemanticKey.getRequestMarshaller()))
                .setResponseMarshaller(withHLC((MethodDescriptor.PrototypeMarshaller<RespT>)
                    methodSemanticKey.getResponseMarshaller()))
                .build());
            return this;
        }

        public BluePrint build() {
            Map<String, MethodDescriptor> methodsMap;
            Map<String, MethodDescriptor> wrappedMethods;
            Map<String, MethodSemantic> methodSemanticMap;
            switch (this.wrappedMethods == null ? 0 : this.wrappedMethods.size()) {
                case 0:
                    methodSemanticMap = emptyMap();
                    methodsMap = emptyMap();
                    wrappedMethods = emptyMap();
                    break;
                case 1: {
                    MethodDescriptor method = this.methods.get(0);
                    String fullMethodName = method.getFullMethodName();
                    methodSemanticMap = singletonMap(fullMethodName, this.methodSemantics.get(0));
                    methodsMap = singletonMap(fullMethodName, method);
                    wrappedMethods = singletonMap(fullMethodName, this.wrappedMethods.get(0));
                }
                break;
                default:
                    methodSemanticMap =
                        new java.util.LinkedHashMap<>(
                            this.wrappedMethods.size() < 1073741824
                                ? 1 + this.wrappedMethods.size() + (this.wrappedMethods.size() - 3) / 3
                                : Integer.MAX_VALUE);
                    methodsMap =
                        new java.util.LinkedHashMap<>(
                            this.methods.size() < 1073741824
                                ? 1 + this.methods.size() + (this.methods.size() - 3) / 3
                                : Integer.MAX_VALUE);
                    wrappedMethods =
                        new java.util.LinkedHashMap<>(
                            this.wrappedMethods.size() < 1073741824
                                ? 1 + this.wrappedMethods.size() + (this.wrappedMethods.size() - 3) / 3
                                : Integer.MAX_VALUE);
                    for (int $i = 0; $i < this.methods.size(); $i++) {
                        MethodDescriptor method = this.methods.get($i);
                        String fullMethodName = method.getFullMethodName();
                        methodSemanticMap.put(fullMethodName, this.methodSemantics.get($i));
                        methodsMap.put(fullMethodName, method);
                        wrappedMethods.put(fullMethodName, this.wrappedMethods.get($i));
                    }
                    methodSemanticMap = unmodifiableMap(methodSemanticMap);
                    methodsMap = unmodifiableMap(methodsMap);
                    wrappedMethods = unmodifiableMap(wrappedMethods);
            }

            return new BluePrint(serviceDescriptor, methodSemanticMap, methodsMap, wrappedMethods);
        }

        private <T> MethodDescriptor.PrototypeMarshaller<T> withHLC(
            MethodDescriptor.PrototypeMarshaller<T> marshaller) {
            return new MethodDescriptor.PrototypeMarshaller<>() {
                private final ThreadLocal<UnknownFieldSet.Builder> localFieldSetBuilder =
                    ThreadLocal.withInitial(() -> UnknownFieldSet.newBuilder());

                private final ThreadLocal<UnknownFieldSet.Field.Builder> localFieldBuilder =
                    ThreadLocal.withInitial(() -> UnknownFieldSet.Field.newBuilder());

                @Override
                public Class<T> getMessageClass() {
                    return marshaller.getMessageClass();
                }

                @Nullable
                @Override
                public T getMessagePrototype() {
                    return marshaller.getMessagePrototype();
                }

                @Override
                public InputStream stream(T value) {
                    UnknownFieldSet.Field hlcField = localFieldBuilder.get().clear().addFixed64(HLC.INST.get()).build();
                    UnknownFieldSet fieldSet =
                        localFieldSetBuilder.get().addField(PIGGYBACK_FIELD_ID, hlcField).build();
                    return marshaller.stream((T) ((Message) value).toBuilder().setUnknownFields(fieldSet).build());
                }

                @Override
                public T parse(InputStream stream) {
                    T message = marshaller.parse(stream);
                    UnknownFieldSet.Field piggybackField =
                        ((Message) message).getUnknownFields().getField(PIGGYBACK_FIELD_ID);
                    HLC.INST.update(piggybackField.getFixed64List().get(0));
                    return message;
                }
            };
        }
    }
}
