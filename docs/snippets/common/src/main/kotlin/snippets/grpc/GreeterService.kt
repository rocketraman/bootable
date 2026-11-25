package snippets.grpc

import com.github.rocketraman.bootable.server.grpc.grpckotlins.grpcRequestHeaders
import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

// tag::service[]
class GreeterService : GreeterGrpcKt.GreeterCoroutineImplBase() {
  override suspend fun sayHello(request: HelloRequest): HelloReply {
    if (request.name.isBlank()) throw StatusException(Status.INVALID_ARGUMENT.withDescription("name is required"))
    return helloReply { message = "Hello, ${request.name}" }
  }

  override fun streamHellos(request: HelloRequest): Flow<HelloReply> = flow {
    repeat(request.count) { emit(helloReply { message = "Hello #${it + 1}, ${request.name}" }) }
  }
}
// end::service[]

// tag::headers[]
private val requestId = Metadata.Key.of("x-request-id", Metadata.ASCII_STRING_MARSHALLER)

suspend fun requestId(): String? = grpcRequestHeaders()?.get(requestId)
// end::headers[]
