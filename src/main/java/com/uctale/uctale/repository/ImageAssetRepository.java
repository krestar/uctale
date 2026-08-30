package com.uctale.uctale.repository;

import com.uctale.uctale.domain.ImageAsset;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ImageAssetRepository extends JpaRepository<ImageAsset, String> {

    Optional<ImageAsset> findByIdAndGameSessionOwnerKey(String id, String ownerKey);
}
