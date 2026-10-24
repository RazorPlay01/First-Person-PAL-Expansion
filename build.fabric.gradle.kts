plugins {
	id("mod-platform")
	id("dev.kikugie.loom-back-compat")
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
		replace("FabricDataOutput", "FabricPackOutput")
	}
	replacements.string(current.parsed >= "26.2") {
		// its name; only 26.3 renames it.
		replace("method = \"renderHandsWithItems\"", "method = \"submitHandsWithItems\"")
	}
	replacements.string(current.parsed >= "26.3") {
		replace("ItemInHandRenderer.class", "FirstPersonHandsAndItemsRenderer.class")
		replace("method = \"renderItem\"", "method = \"submitArmWithItem\"")
	}
}

tasks.matching { it.name in listOf("compileJava", "compileKotlin", "kspKotlin", "javadoc", "sourcesJar") }
	.configureEach { dependsOn(tasks.named("stonecutterGenerate")) }

platform {
	loader = "fabric"
	dependencies {
		required("minecraft") {
			fabricLikeVersionRange = prop("deps.minecraft")
		}
		required("fabric-api") {
			slug("fabric-api")
			fabricLikeVersionRange = ">=${prop("deps.fabric-api")}"
		}
		required("fabricloader") {
			fabricLikeVersionRange = ">=${prop("deps.fabric-loader")}"
		}
		optional("modmenu") {}
	}
}

loom {
	accessWidenerPath = rootProject.file("src/main/resources/aw/${sc.current.version}.accesswidener")
	val devJvmArgs = propsList("mod", "dev_jvm_args") +
		propsList("mod", "dev_jvm_args_mixin_debug")
	runs.named("client") {
		client()
		generateRunConfig.set(true)
		runDirectory.set(layout.projectDirectory.dir("run"))
		programArguments.add("--username=Dev")
		jvmArguments.addAll(devJvmArgs)
		displayName.set("Fabric Client")
	}
	runs.named("server") {
		server()
		generateRunConfig.set(true)
		runDirectory.set(layout.projectDirectory.dir("run"))
		jvmArguments.addAll(devJvmArgs)
		displayName.set("Fabric Server")
	}
}

fabricApi {
	configureDataGeneration {
		outputDirectory = file("${rootDir}/versions/datagen/${sc.current.version}/src/main/generated")
		client = true
	}
}

repositories {
	mavenCentral()
	mavenLocal()
	strictMaven("https://maven.terraformersmc.com/", "com.terraformersmc") { name = "TerraformersMC" }
	strictMaven("https://api.modrinth.com/maven", "maven.modrinth") { name = "Modrinth" }
	strictMaven("https://repo.redlance.org/public")
	strictMaven("https://maven.shedaniel.me/")
	strictMaven("https://maven.kosmx.dev/")
}

configurations.all {
	resolutionStrategy {
		force("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	}
}

dependencies {
	minecraft("com.mojang:minecraft:${prop("deps.minecraft")}")
	if (sc.current.parsed < "26") {
		mappings(loom.layered {
			officialMojangMappings()
			if (sc.properties.contains("deps.parchment"))
				parchment("org.parchmentmc.data:parchment-${prop("deps.parchment")}@zip")
		})
	}
	modImplementation("net.fabricmc:fabric-loader:${prop("deps.fabric-loader")}")
	// implementation(libs.moulberry.mixinconstraints)
	// include(libs.moulberry.mixinconstraints)
	modImplementation("net.fabricmc.fabric-api:fabric-api:${prop("deps.fabric-api")}")
	modLocalRuntime("com.terraformersmc:modmenu:${prop("deps.modmenu")}")
	modImplementation("com.zigythebird.playeranim:PlayerAnimationLibFabric:${prop("deps.player_animation_library")}")
}
