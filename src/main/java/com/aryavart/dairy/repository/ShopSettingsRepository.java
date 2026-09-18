package com.aryavart.dairy.repository;

import com.aryavart.dairy.model.ShopSettings;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShopSettingsRepository extends MongoRepository<ShopSettings, String> {
}
