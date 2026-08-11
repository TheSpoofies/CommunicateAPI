# CommunicateAPI

Send arbitrary-length data packets from a Paper server to a specific client
over a dedicated plugin messaging channel. Pairs with a client-side Fabric
mod that registers a matching `ClientPlayNetworking` receiver for that
channel, then parses and dispatches the payload.

Each consumer picks their own prefix, which becomes the channel name
(`<prefix>:data`), so multiple plugins can use this system on the same
server without colliding.

## Installation

Add the JitPack repository and the dependency to your `pom.xml`:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependencies>
    <dependency>
        <groupId>com.github.TheSpoofies</groupId>
        <artifactId>CommunicateAPI</artifactId>
        <version>v1.0.0</version>
        <scope>provided</scope>
    </dependency>
</dependencies>
```

> Use `provided` scope — the API is loaded on the server as its own plugin
> (see below), so it should not be shaded into your plugin's jar.

Then add CommunicateAPI as a dependency in your own `plugin.yml` so Paper
loads it first:

```yaml
depend: [CommunicateAPI]
```

Finally, drop `CommunicateAPI-<version>.jar` into your server's `plugins/`
folder — it needs to be present and enabled at runtime, not just on your
build classpath.

## Usage

```java
import the.spoofies.communicateAPI.ChatDataMessenger;

// pass your own JavaPlugin instance — the messenger registers its
// outgoing channel under your plugin, not CommunicateAPI's
ChatDataMessenger messenger = ChatDataMessenger.create(this, "myplugin");

// simple form, as many fields as you want
messenger.send(player, /* type */ 1, abilityId, cooldownTicks, maxCooldownTicks);

// builder form, for readability with lots of fields
messenger.packet(1)
    .with(abilityId)
    .with(cooldownTicks)
    .with(maxCooldownTicks)
    .send(player);
```

The `type` argument is an `int` you define the meaning of yourself — the
messenger doesn't interpret it, so keep your own type-ID constants in your
plugin and share them with whatever parses the data on the client.

### Prefix rules

Your prefix becomes half of a Minecraft channel identifier
(`<prefix>:data`), so it must be lowercase letters, digits, `-`, or `_`
only. `create()` throws `IllegalArgumentException` immediately if the
prefix doesn't match, rather than failing later when the channel is used.

### Wire format

Each packet is written as: a varint packet type, a varint field count,
then each field encoded as a length-prefixed UTF-8 string. Fields are sent
as their `String.valueOf(...)` form regardless of original type, so parse
them back into the type you expect on the client.

## Client side

This repo only covers the server-side sending half. On the client, a
Fabric mod needs to:
1. Register a `CustomPacketPayload` matching this wire format
2. Register it via `PayloadTypeRegistry.clientboundPlay()` in its common
   `ModInitializer`
3. Handle it with `ClientPlayNetworking.registerGlobalReceiver` for the
   `<prefix>:data` channel

See the project's mod repo for the matching payload/codec and receiver
setup.

More coming soon...

## Versioning

New releases are plain git tags (`v1.0.1`, `v1.1.0`, etc.) — bump the
version in your `pom.xml` dependency block to pick up changes. Existing
tags are never overwritten.