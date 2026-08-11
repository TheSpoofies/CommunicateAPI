# CommunicateAPI

Send arbitrary-length data packets from a Paper server to a specific client,
disguised as a chat message. Pairs with a client-side mixin (Fabric) that
intercepts and cancels messages matching your chosen prefix before they
render, then parses and dispatches the payload.

Each consumer picks their own prefix, so multiple plugins can use this
system on the same server without colliding.

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

ChatDataMessenger messenger = ChatDataMessenger.create("myplugin");

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

### Wire format

```
<prefix>_<type>_<field0>_<field1>_..._<fieldN>
```

If your data might contain `_`, use a custom delimiter instead:

```java
ChatDataMessenger messenger = ChatDataMessenger.create("myplugin", ":");
```

`send()` throws `IllegalArgumentException` if any field's string value
contains the delimiter, so a mismatch is caught immediately rather than
silently corrupting the packet.

## Client side

This repo only covers the server-side sending half. On the client, a Fabric
mixin needs to intercept incoming system chat packets, check for your
prefix, cancel the packet, and route the rest of the string to your own
parser/handler. See the project's mod repo for the matching mixin and
packet-type registry.

More coming soon...

## Versioning

New releases are plain git tags (`v1.0.1`, `v1.1.0`, etc.) — bump the
version in your `pom.xml` dependency block to pick up changes. Existing
tags are never overwritten.
