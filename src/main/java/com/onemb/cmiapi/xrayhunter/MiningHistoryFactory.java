package com.onemb.cmiapi.xrayhunter;

/** Creates the mining-history integration owned by one feature lifecycle. */
@FunctionalInterface
public interface MiningHistoryFactory {
    MiningHistory create(XRayHunterFeature feature);
}
