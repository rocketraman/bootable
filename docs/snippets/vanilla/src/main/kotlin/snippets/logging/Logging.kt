package snippets.logging

import com.github.rocketraman.bootable.boot.Bootable
import com.github.rocketraman.bootable.boot.boot
import com.github.rocketraman.bootable.logging.BootLoggingLevel
import com.github.rocketraman.bootable.logging.log4j2.LoggingType
import com.github.rocketraman.bootable.logging.log4j2.loggingInit
import org.apache.logging.log4j.kotlin.logger

// tag::boot[]
fun main() = boot(loggingType = LoggingType.JSON) {  // or leave it to LOGGING_TYPE
  Bootable(setOf(/* ... the application's services */))
}
// end::boot[]

// tag::logger[]
class Greeter {
  private val log = logger()

  fun greet(name: String) = log.info { "greeting $name" }
}
// end::logger[]

// tag::cli[]
fun cliMain() {
  loggingInit(
    redirectStandardOutErr = false,             // keep standard output for the tool's own output
    loggingType = LoggingType.PLAIN,
    overrideLoggingLevel = BootLoggingLevel.WARN,
  )
  // ... the tool's work
}
// end::cli[]
