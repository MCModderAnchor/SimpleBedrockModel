# SimpleBedrockModel

[![License](https://img.shields.io/badge/License-LGPL--3.0-blue?style=for-the-badge)](LICENSE)
[![Modrinth](https://img.shields.io/badge/Modrinth-Available-00AF5C?style=for-the-badge&logo=modrinth)](https://modrinth.com/mod/simplebedrockmodel)

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
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    jarJar(implementation(fg.deobf("maven.modrinth:simplebedrockmodel:1.5.0-forge+1.20.1"))) {
        jarJar.ranged(it, "[1.5.0,)")
    }
    // The animation library is already included in jar (jar in jar), 
    // but since modrinth maven cannot handle transitive dependencies,
    // you need to include it to pass the compilation.
    compileOnly("com.maydaymemory:mae:1.0.2")
}
```

#### Minecraft 1.21.1 (NeoForge)
```groovy
repositories {
    maven {
        url = "https://api.modrinth.com/maven"
        content {
            includeGroup "maven.modrinth"
        }
    }
}

dependencies {
    implementation jarJar("maven.modrinth:simplebedrockmodel:1.5.0-neoforge+mc1.21.1") {
        version {
            prefer '1.5.0-neoforge+1.21.1'
        }
    }
    // The animation library is already included in jar (jar in jar), 
    // but since modrinth maven cannot handle transitive dependencies,
    // you need to include it to pass the compilation.
    compileOnly("com.maydaymemory:mae:1.0.2")
}
```

## 📖 Usage Examples

### Loading a Bedrock Model

There is a utility class "GsonUtil" to help you create pojo from json:

```java
InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
Gson gson = GsonUtil.GSON;
BedrockModelPOJO pojo = gson.fromJson(reader, BedrockModelPOJO.class);
BedrockModel model = new BedrockModel(pojo);
```

### Loading a Bedrock Animations

There is a utility class "GsonUtil" to help you create pojo from json:
```java
BedrockModel model = ...;
BoneIndexProvider indexProvider = new BedrockModelBoneIndexProvider(model);
InputStreamReader reader = new InputStreamReader(inputStream, StandardCharsets.UTF_8);
Gson gson = GsonUtil.GSON;
BedrockAnimationFile pojo = gson.fromJson(reader, BedrockAnimationFile.class);
```

**Animation Instance Construct:** A utility class "Animations" could help construct animation instance as long as you got the pojo.

```java
BedrockAnimationFile animationFilePojo = ...;
BedrockAnimationPOJO animationPojo = ...;
BedrockModel model = ...;
BoneIndexProvider indexProvider = new BedrockModelBoneIndexProvider(model);
// BedrockAnimationFile represents a complete Bedrock Edition animation file, 
// which containing multiple animations.
List<BedrockAnimation> animations = Animations.createAnimation(animationFilePojo, indexProvider);
// BedrockAnimationPOJO represents a single animation.
BedrockAnimation animation = Animations.createAnimation("animation_name", animationPojo, indexProvider);
```

### Create Animation Runner

Animation Runner can control the progress of animation according to time, and estimate Pose, Animation Events and Curves according to the progress.

```java
BedrockAnimation animation = ...;
// use getSpecifiedEndTimeS because bedrock animation has its own end time.
AnimationContext animationContext = new AnimationContext(animation.getSpecifiedEndTimeS()); 
animationContext.setState(new LoopingState(System::nanoTime));
AnimationRunner animationRunner = new AnimationRunner(animation, animationContext);

// somewhere else
Pose pose = animationRunner.evaluate();
```

### Apply animations to model

```java
// According to the Bedrock Edition animation standard,
// this type of blender must be used and ZYX BoneTransform must be used.
AdditiveBlender blender = new SimpleAdditiveBlender(new ZYXBoneTransformFactory(), ArrayPoseBuilder::new);

// Before rendering
Pose bindPose = model.getBindPose(); // The initial pose of the model
Pose animationPose = ...; // For example, animationRunner.evaluate()
Pose blended = blender.blend(bindPose, animationPose);
// Apply animation to model
model.applyPose(blended);
// Rendering
model.renderToBuffer(poseStack, buffer, packedLight, packedOverlay);
// After Rendering, You can choose whether to restore to binding pose according to your usage.
// model.applyPose(bindPose);
```

### How to use animation blending

[See the documentation of Mayday Animation Engine](https://github.com/286799714/MaydayAnimationEngine)

## 🏗️ Project Structure

```
src/main/java/com/github/mcmodderanchor/simplebedrockmodel/
├── v1/client/bedrock/
│   ├── model/           # Core model classes
│   ├── animation/       # Animation system
│   ├── pojo/           # Data transfer objects
│   └── compat/         # Compatibility layers
└── example/            # Usage examples, not included in builds
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
