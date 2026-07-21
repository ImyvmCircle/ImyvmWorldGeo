package com.imyvm.iwg.infra.dynmap

import com.imyvm.iwg.domain.Region
import com.imyvm.iwg.domain.component.CircleGeometry
import com.imyvm.iwg.domain.component.GeoScope
import com.imyvm.iwg.domain.component.PolygonGeometry
import com.imyvm.iwg.domain.component.RectangleGeometry
import com.imyvm.iwg.domain.component.UnknownGeometry
import net.minecraft.resources.Identifier

internal enum class DynmapMarkerKind {
    AREA,
    CIRCLE,
    POINT
}

internal data class DynmapMarkerIdentity(
    val kind: DynmapMarkerKind,
    val id: String
)

internal sealed interface DynmapMarkerSpec {
    val identity: DynmapMarkerIdentity
    val label: String
    val world: String
}

internal class DynmapAreaMarkerSpec(
    override val identity: DynmapMarkerIdentity,
    override val label: String,
    override val world: String,
    val x: DoubleArray,
    val z: DoubleArray,
    val color: Int
) : DynmapMarkerSpec

internal data class DynmapCircleMarkerSpec(
    override val identity: DynmapMarkerIdentity,
    override val label: String,
    override val world: String,
    val centerX: Double,
    val centerZ: Double,
    val radius: Double,
    val color: Int
) : DynmapMarkerSpec

internal data class DynmapPointMarkerSpec(
    override val identity: DynmapMarkerIdentity,
    override val label: String,
    override val world: String,
    val x: Double,
    val y: Double,
    val z: Double
) : DynmapMarkerSpec

internal data class DynmapMarkerFailure(
    val identity: DynmapMarkerIdentity,
    val cause: Exception
)

internal data class DynmapReconciliationResult(
    val upsertFailures: List<DynmapMarkerFailure>,
    val deletionFailures: List<DynmapMarkerFailure>
)

internal fun buildDynmapProjection(regions: List<Region>): List<DynmapMarkerSpec> = buildList {
    for (region in regions) {
        if (!region.showOnDynmap) continue
        val color = DynmapColorResolver.resolveColor(region)
        for (scope in region.scopes) {
            if (scope.showOnDynmap) addAll(projectScope(region, scope, color))
        }
    }
}.also { projection ->
    require(projection.map { it.identity }.toSet().size == projection.size) {
        "Dynmap projection contains duplicate marker identities"
    }
}

internal fun <E> reconcileDynmapProjection(
    desired: List<DynmapMarkerSpec>,
    existing: List<E>,
    identityOf: (E) -> DynmapMarkerIdentity,
    upsert: (DynmapMarkerSpec) -> Unit,
    delete: (E) -> Unit
): DynmapReconciliationResult {
    val desiredIdentities = desired.mapTo(linkedSetOf()) { it.identity }
    require(desiredIdentities.size == desired.size) { "Dynmap projection contains duplicate marker identities" }

    val upsertFailures = mutableListOf<DynmapMarkerFailure>()
    for (spec in desired) {
        try {
            upsert(spec)
        } catch (error: Exception) {
            upsertFailures.add(DynmapMarkerFailure(spec.identity, error))
        }
    }
    if (upsertFailures.isNotEmpty()) {
        return DynmapReconciliationResult(upsertFailures, emptyList())
    }

    val deletionFailures = mutableListOf<DynmapMarkerFailure>()
    for (marker in existing) {
        val identity = identityOf(marker)
        if (identity in desiredIdentities) continue
        try {
            delete(marker)
        } catch (error: Exception) {
            deletionFailures.add(DynmapMarkerFailure(identity, error))
        }
    }
    return DynmapReconciliationResult(emptyList(), deletionFailures)
}

private fun projectScope(region: Region, scope: GeoScope, color: Int): List<DynmapMarkerSpec> {
    val shape = scope.geoShape ?: return emptyList()
    val label = "${region.name}:${scope.scopeName}"
    val world = dynmapWorldName(scope.worldId)
    val boundaryId = dynmapScopeMarkerId(scope)
    val boundary = when (val geometry = shape.typedGeometry) {
        is CircleGeometry -> DynmapCircleMarkerSpec(
            DynmapMarkerIdentity(DynmapMarkerKind.CIRCLE, boundaryId),
            label,
            world,
            geometry.centerX.toDouble(),
            geometry.centerZ.toDouble(),
            geometry.radius.toDouble(),
            color
        )
        is RectangleGeometry -> DynmapAreaMarkerSpec(
            DynmapMarkerIdentity(DynmapMarkerKind.AREA, boundaryId),
            label,
            world,
            doubleArrayOf(
                geometry.west.toDouble(),
                geometry.east.toDouble(),
                geometry.east.toDouble(),
                geometry.west.toDouble()
            ),
            doubleArrayOf(
                geometry.north.toDouble(),
                geometry.north.toDouble(),
                geometry.south.toDouble(),
                geometry.south.toDouble()
            ),
            color
        )
        is PolygonGeometry -> DynmapAreaMarkerSpec(
            DynmapMarkerIdentity(DynmapMarkerKind.AREA, boundaryId),
            label,
            world,
            DoubleArray(geometry.vertexCount) { geometry.x(it).toDouble() },
            DoubleArray(geometry.vertexCount) { geometry.z(it).toDouble() },
            color
        )
        UnknownGeometry -> return emptyList()
    }

    return buildList {
        add(boundary)
        scope.teleportPoint?.let { point ->
            add(
                DynmapPointMarkerSpec(
                    DynmapMarkerIdentity(DynmapMarkerKind.POINT, dynmapTeleportMarkerId(scope)),
                    label,
                    world,
                    point.x.toDouble(),
                    point.y.toDouble(),
                    point.z.toDouble()
                )
            )
        }
    }
}

internal fun dynmapWorldName(worldId: Identifier): String = when (worldId.toString()) {
    "minecraft:overworld" -> "world"
    "minecraft:the_nether" -> "world_nether"
    "minecraft:the_end" -> "world_the_end"
    else -> worldId.path
}
