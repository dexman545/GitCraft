package dex.mcgitmaker

import dex.mcgitmaker.data.Artifact
import dex.mcgitmaker.data.McVersion
import dex.mcgitmaker.data.outlet.McFabric
import dex.mcgitmaker.data.outlet.McOutletMeta
import groovy.json.JsonGenerator
import groovy.json.JsonParserType
import groovy.json.JsonSlurper
import net.fabricmc.loader.api.SemanticVersion
import net.fabricmc.loader.impl.game.minecraft.McVersionLookup

import java.nio.file.Path
import java.nio.file.Paths
import java.time.ZoneId
import java.time.ZonedDateTime

class Util {
    static enum MappingsNamespace {
        OFFICIAL,
        MOJMAP

        @Override
        String toString() {
            return name().toLowerCase(Locale.ENGLISH)
        }
    }

    static def saveMetadata(Map<String, McVersion> data) {
        def generator = new JsonGenerator.Options()
                .addConverter(Path) { it.toFile().canonicalPath }
                .build()

        def x = GitCraft.METADATA_STORE.toFile()
        x.createNewFile()
        x.write(generator.toJson(data))
    }

    static def addLoaderVersion(McVersion mcVersion) {
        if (mcVersion.loaderVersion == null) {
            // Attempt lookup in Outlet database as newer MC versions require a loader update
            def v = Outlet.INSTANCE.outletDatabase.versions.find {
                it.id == mcVersion.version
            }
            if (v != null) {
                println 'Successfully looked up new semver version...'
                mcVersion.loaderVersion = v.normalized
                return
            }

            println 'Creating new semver version...'
            def x = McVersionLookup.getVersion(
                List.of(mcVersion.artifacts.clientJar.fetchArtifact().toPath()), mcVersion.mainClass, null
            )
            mcVersion.loaderVersion = x.normalized
            println 'Semver made for: ' + x.raw + ' as ' + x.normalized
            println 'If generated semver is incorrect, it will break the order of the generated repo. ' +
                'Consider updating Fabric Loader.'
        }
    }

    static TreeMap<SemanticVersion, McVersion> orderVersionMap(LinkedHashMap<String, McVersion> metadata) {
        def ORDERED_MAP = new TreeMap<SemanticVersion, McVersion>()
        println 'Sorting on semver MC versions...'
        metadata.values().each {it ->
            if (it.hasMappings) {
                addLoaderVersion(it)
                ORDERED_MAP.put(SemanticVersion.parse(it.loaderVersion), it)
            }
        }

        return ORDERED_MAP
    }

    //todo make work
    static def updateMcVersionPath(McVersion mcVersion) {
        def root = GitCraft.MAIN_ARTIFACT_STORE.parent

        if (mcVersion.mergedJar != null) {
            def p = Paths.get(mcVersion.mergedJar)
            def po = p.toString()
            for (int i in 1..p.getNameCount()-1) {
                if (p.getName(i).toString() == 'artifact-store') {
                    p = p.subpath(i, p.getNameCount())
                }
            }

            mcVersion.mergedJar = root.resolve(p)

            println 'Remapped ' + po + ' to ' + mcVersion.mergedJar
        }

        mcVersion.libraries.each {updateArtifactPath(it as Artifact)}
        updateArtifactPath(mcVersion.artifacts.clientMappings)
        updateArtifactPath(mcVersion.artifacts.clientJar)
        updateArtifactPath(mcVersion.artifacts.serverJar)
        updateArtifactPath(mcVersion.artifacts.serverMappings)
    }

    static def updateArtifactPath(Artifact artifact) {
        def root = GitCraft.MAIN_ARTIFACT_STORE.parent
        if (artifact.containingPath != null) {
            def p = artifact.containingPath
            def po = p.toString()
            for (int i in 1..p.getNameCount()-1) {
                if (p.getName(i).toString() == 'artifact-store') {
                    p = p.subpath(i, p.getNameCount())
                }
            }

            artifact.containingPath = root.resolve(p)

            println 'Remapped ' + po + ' to ' + artifact.containingPath
        }
    }

    enum Outlet {
        INSTANCE();

        public McOutletMeta outletDatabase = new McOutletMeta(lastChanged: Date.from(ZonedDateTime.of(2012, 1, 1, 0, 0, 0, 0, ZoneId.systemDefault()).toInstant()), versions: [])
        private final def OUTLET_DATABASE = 'https://raw.githubusercontent.com/dexman545/outlet-database/master/mc2fabric.json'
        Outlet() {
            outletDatabase = new JsonSlurper(type: JsonParserType.INDEX_OVERLAY).parse(new URL(OUTLET_DATABASE)) as McOutletMeta
        }
    }
}
