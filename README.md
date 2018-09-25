## Utils

This repository contains different general utility objects.

#### Features
* Serialization (via Jackson)
* Compression (via Deflater)
* Crypto (keys, hashes, signatures)

#### Usage

##### Install via Gradle
```groovy
repositories {
  ...
  maven { url 'https://jitpack.io' }
  ...
}

...

dependencies {
  ...
  implementation "com.github.seniorjoinu:utils:$utils_version"
  ...
}
```

##### Serialization
```kotlin
val serializedContent = SerializationUtils.anyToJSON(content)
val deserializedContent: Content = SerializationUtils.jSONToAny(serializedContent)

println(serializedContent == content) // true
```

##### Compression
```kotlin
val compressedBytes = CompressionUtils.compress(bytes)
val decompressedBytes = CompressionUtils.decompress(compressedBytes)

println(compressedBytes.size < bytes.size) // true
println(decompressedBytes.contentEquals(bytes)) // true
```

##### Basic cryptography
```kotlin
val keyPair = CryptoUtils.generateKeyPair()
val signedData = CryptoUtils.sign(data, data1) { keyPair.private }
val valid = CryptoUtils.verify(signedData, data, data1) { keyPair.public }
println(valid) // true

val hash = CryptoUtils.hash(data, data1, data2)
println(hash.size <= 256) // true
```