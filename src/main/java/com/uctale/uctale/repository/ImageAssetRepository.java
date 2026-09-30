package com.uctale.uctale.repository;

import com.uctale.uctale.domain.ImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {

    Optional<ImageAsset> findByIdAndGameSessionOwnerKey(String id, String ownerKey);

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE image_asset
            SET generation_owner = :owner,
                generation_lease_expires_at = :leaseExpiresAt
            WHERE id = :id
              AND image_bytes IS NULL
              AND (
                    generation_owner IS NULL
                    OR generation_lease_expires_at IS NULL
                    OR generation_lease_expires_at < :now
              )
            """, nativeQuery = true)
    int claimGeneration(
            @Param("id") String id,
            @Param("owner") String owner,
            @Param("now") LocalDateTime now,
            @Param("leaseExpiresAt") LocalDateTime leaseExpiresAt
    );

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE image_asset
            SET content_type = :contentType,
                image_bytes = :bytes,
                generated_at = :generatedAt,
                generation_owner = NULL,
                generation_lease_expires_at = NULL
            WHERE id = :id
              AND generation_owner = :owner
              AND image_bytes IS NULL
            """, nativeQuery = true)
    int storeGeneratedImageIfOwner(
            @Param("id") String id,
            @Param("owner") String owner,
            @Param("contentType") String contentType,
            @Param("bytes") byte[] bytes,
            @Param("generatedAt") LocalDateTime generatedAt
    );

    @Modifying
    @Transactional
    @Query(value = """
            UPDATE image_asset
            SET generation_owner = NULL,
                generation_lease_expires_at = NULL
            WHERE id = :id
              AND generation_owner = :owner
              AND image_bytes IS NULL
            """, nativeQuery = true)
    int releaseGenerationClaim(@Param("id") String id, @Param("owner") String owner);
}
