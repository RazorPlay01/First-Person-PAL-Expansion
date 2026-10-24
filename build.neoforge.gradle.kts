plugins {
	id("mod-platform")
	id("net.neoforged.moddev")
}

stonecutter {
	replacements.string(current.parsed >= "1.21.11") {
		replace("ResourceLocation", "Identifier")
		replace("location()", "identifier()")
		replace("net.minecraft.client.model.PlayerModel", "net.minecraft.client.model.player.PlayerModel")
		replace("net.minecraft.client.renderer.RenderType", "net.minecraft.client.renderer.rendertype.RenderType")
		replace("RenderType.entity", "RenderTypes.entity")
		replace("net.minecraft.client.renderer.entity.player.PlayerRenderer", "net.minecraft.client.renderer.entity.player.AvatarRenderer")
		replace("PlayerRenderer", "AvatarRenderer")
		replace("IAnimatedPlayer", "IAnimatedAvatar")
		replace("PlayerAnimManager", "AvatarAnimManager")
		replace("this.player instanceof", "this.avatar instanceof")
		replace("RegisterClientReloadListenersEvent", "AddClientReloadListenersEvent")
		replace("event.registerReloadListener(new FpAnimationResources());", "event.addListener(FpAnimationResources.KEY, new FpAnimationResources());")
	}
	replacements.string(current.parsed >= "26.1.2") {
		replace("getPosX()", "position.x")
		replace("getPosY()", "position.y")
		replace("getPosZ()", "position.z")
		replace("getRotX()", "rotation.x")
		replace("getRotY()", "rotation.y")
		replace("getRotZ()", "rotation.z")
		replace("getScaleX()", "scale.x")
		replace("getScaleY()", "scale.y")
		replace("getScaleZ()", "scale.z")
	}
	replacements.string(current.parsed >= "26.2") {
		replace("method = \"renderHandsWithItems\"", "method = \"submitHandsWithItems\"")
	}
	replacements.string(current.parsed >= "26.3") {
		replace("ItemInHandRenderer.class", "FirstPersonHandsAndItemsRenderer.class")
		replace("method = \"renderItem\"", "method = \"submitArmWithItem\"")
	}
}

sourceSets["main"].java.setSrcDirs(
	listOf(layout.buildDirectory.dir("generated/stonecutter/main/java").get().asFile)
)
tasks.matching { it.name in listOf("compileJava", "compileKotlin", "kspKotlin", "javadoc", "sourcesJar") }
	.configureEach { dependsOn(tasks.named("stonecutterGenerate")) }

platform {
	loader = "neoforge"
	dependencies {
		required("minecraft") {
			forgeLikeVersionRange = prop("deps.minecraft")
		}
		required("neoforge") {
			forgeLikeVersionRange.set("[${prop("deps.neoforge")},)")
		}
	}
}

neoForge {
	version = prop("deps.neoforge")
	accessTransformers.from(rootProject.file("src/main/resources/aw/${stonecutter.current.version}.cfg"))
	validateAccessTransformers = true

	if (sc.properties.contains("deps.parchment")) parchment {
		val (mc, ver) = prop("deps.parchment").split(':')
		mappingsVersion = ver
		minecraftVersion = mc
	}

	runs {
		val devJvmArgs = propsList("mod", "dev_jvm_args") +
			propsList("mod", "dev_jvm_args_mixin_debug")
		register("client") {
			client()
			gameDirectory = file("run/")
			ideName = "NeoForge Client (${stonecutter.current.version})"
			programArgument("--username=Dev")
			jvmArguments.addAll(devJvmArgs)
		}
		register("server") {
			server()
			gameDirectory = file("run/")
			ideName = "NeoForge Server (${stonecutter.current.version})"
			jvmArguments.addAll(devJvmArgs)
		}
	}

	mods {
		register(prop("mod.id")) {
			sourceSet(sourceSets["main"])
		}
	}
	sourceSets["main"].resources.srcDir("${rootDir}/versions/datagen/${sc.current.version}/src/main/generated")
}

repositories {
	mavenCentral()
	mavenLocal()
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
	strictMaven("https://repo.redlance.org/public")
	strictMaven("https://maven.shedaniel.me/")
	strictMaven("https://maven.kosmx.dev/")
}

dependencies {
	configurations.all {
		resolutionStrategy {
			if (stonecutter.current.parsed < "1.21.11") {
				force("com.google.code.gson:gson:2.10")
				force("io.netty:netty-buffer:4.1.118.Final")
				force("io.netty:netty-common:4.1.118.Final")
				force("it.unimi.dsi:fastutil:8.5.12")
				force("org.slf4j:slf4j-api:2.0.13")
				force("org.joml:joml:1.10.8")
			}
		}
	}
	implementation("com.zigythebird.playeranim:PlayerAnimationLibNeo:${prop("deps.player_animation_library")}") {
		if (stonecutter.current.parsed >= "1.21.11") {
			exclude(group = "com.google.code.gson")
			exclude(group = "io.netty")
			exclude(group = "it.unimi.dsi")
			exclude(group = "org.slf4j")
			exclude(group = "org.joml")
		}
	}
	// implementation(libs.moulberry.mixinconstraints)
	// jarJar(libs.moulberry.mixinconstraints)
}

tasks.named("createMinecraftArtifacts") {
	dependsOn(tasks.named("stonecutterGenerate"))
}
