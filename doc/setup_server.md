## Windows

Executing these commands will clone and build the sources, download the fabric server jar and required mods and start
the server.  
This requires `git`, `gradle` and `curl`(and `java`, but what are you doing with Minecraft if you haven't installed
Java) to be installed and on the path.

```bash
set OPENAI_KEY=
git clone https://github.com/YanWittmann/gptalk-fabric-plugin
cd gptalk-fabric-plugin
gradle build
```

The gradle build process can take quite some time on the first execution. After it is done running, run this:

```bash
mkdir demo-server
cd demo-server
curl -OJ https://meta.fabricmc.net/v2/versions/loader/1.19.3/0.14.14/0.11.1/server/jar
java -jar fabric-server-mc.1.19.3-loader.0.14.14-launcher.0.11.1.jar nogui
echo|set /p="eula=true" > eula.txt
unix2dos eula.txt eula.txt
copy ..\build\libs\gptalk-1.0-SNAPSHOT.jar mods
curl https://mediafilez.forgecdn.net/files/4373/752/fabric-api-0.73.2%2B1.19.3.jar --output mods/api-0.73.2+1.19.3.jar
java -jar fabric-server-mc.1.19.3-loader.0.14.14-launcher.0.11.1.jar nogui
```

A few explanations:

- enter your OpenAi API key in the `set OPENAI_KEY=` command
- the commands cannot be entered in one batch, as the `gradle build` command will read the rest of the commands before
  terminating
