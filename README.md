# SimpleBedrockModel

[![License](https://img.shields.io/badge/License-LGPL--3.0-blue?style=for-the-badge)](LICENSE)

> A simple library for loading and rendering Minecraft Bedrock Edition entity models and animations in Java Edition

## 🎯 Supported Features

- ✅ **Bedrock Model**: Full support Bedrock Model loading and rendering
- ✅ **Bedrock Animation**: Complex bone structures and transformations
- ⌛ **Molang Support**: In progress...
- ✅ **Animation Running**: A full-featured animation player.
- ✅ **Animation Blending**: Support for blendspace, layered, kinematic interpolation blending
- ✅ **Resource Pack Support**: Load models and animations from resource packs
- ⌛ **SnowStorm Particle Support**: In progress...

## 🚀 Quick Start

### For Mod Developers

Add SimpleBedrockModel to your mod's dependencies:

#### Minecraft 1.20.1 (Forge)
```groovy
repositories {
    maven {
        url 'https://jitpack.io'
        content {
            includeGroup "com.github.mcmodderanchor"
        }
    }
}

dependencies {
    jarJar(implementation(fg.deobf("com.github.mcmodderanchor:simplebedrockmodel:2.5.1-forge-mc1.20.1"))) {
        jarJar.ranged(it, "[2.5.1,)")
    }
    // The animation library is already included in jar (jar in jar), 
    // but since we exclude transitive dependencies,
    // you need to include it to pass the compilation.
    compileOnly("com.maydaymemory:mae:1.1.4") {
        exclude group: 'com.google.code.findbugs', module: 'jsr305'
        exclude group: 'it.unimi.dsi', module: 'fastutil'
        exclude group: 'org.joml', module: 'joml'
    }
}
```

#### Minecraft 1.21.1 (NeoForge)
```groovy
repositories {
    maven {
        url 'https://jitpack.io'
        content {
            includeGroup "com.github.mcmodderanchor"
        }
    }
}

dependencies {
    implementation jarJar("com.github.mcmodderanchor:simplebedrockmodel:2.5.1-neoforge-mc1.21.1") {
        version {
            prefer '2.5.1'
        }
    }
    // The animation library is already included in jar (jar in jar), 
    // but since we exclude transitive dependencies,
    // you need to include it to pass the compilation.
    compileOnly("com.maydaymemory:mae:1.1.4") {
        exclude group: 'com.google.code.findbugs', module: 'jsr305'
        exclude group: 'it.unimi.dsi', module: 'fastutil'
        exclude group: 'org.joml', module: 'joml'
    }
}
```

## 📝 License

This project is licensed under the **LGPL-3.0 License** - see the [LICENSE](LICENSE) file for details.  

Specifically, assets under the example namespace are provided solely as tutorial examples and for reference. Their respective owners retain **ALL RIGHTS**.  
Please do not reuse or redistribute them without permission.

## 👥 Contributors

- **TartaricAcid** - Lead Developer
- **MaydayMemory** - Core Developer
- **xjqsh** - Core Developer
- **MoePus** - Developer
- **Hidomatn** - Developer

## 🤝 Contributing

We welcome contributions! Please feel free to submit issues and pull requests.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add some amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

## 📞 Support

- **Issues**: [GitHub Issues](https://github.com/mcmodderanchor/SimpleBedrockModel/issues)
- **Discussions**: [GitHub Discussions](https://github.com/mcmodderanchor/SimpleBedrockModel/discussions)
- **Modrinth**: [Modrinth Page](https://modrinth.com/mod/simplebedrockmodel)

## 🔗 Related Projects

- [MaydayAnimationEngine](https://github.com/286799714/MaydayAnimationEngine) - Providing animation infrastructure
- [mocha](https://github.com/unnamed/mocha) - Providing molang support
- [Particle Storm](https://github.com/westernat/ParticleStorm) - The “snowstorm particle” implementation in this project is largely inspired by this project.

<div align="center">
Made with ❤️ by the SimpleBedrockModel Team
</div>
