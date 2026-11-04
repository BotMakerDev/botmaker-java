# botmaker-java

Reads and writes a BotMaker bot's Java through JDT trees: one parser, the value grammar, and the fit of a bot's
parameters. A library with no state, files or UI, used by Studio, the `botmaker` command and the SDK during a run.

```xml
<dependency>
    <groupId>com.github.BotMakerDev</groupId>
    <artifactId>botmaker-java</artifactId>
    <version>TAG</version>
</dependency>
```

Declare `botmaker-studio-api` beside it: this module has it at `provided` scope.
