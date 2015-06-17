/*
 * ARX: Powerful Data Anonymization
 * Copyright 2012 - 2015 Florian Kohlmayer, Fabian Prasser
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.deidentifier.arx.algorithm;

import org.deidentifier.arx.framework.check.history.History.StorageStrategy;
import org.deidentifier.arx.framework.lattice.NodeAction;

/**
 * This class parameterizes a phase the interwoven two-phase Flash algorithm.
 *
 * @author Fabian Prasser
 * @author Florian Kohlmayer
 */
public class FLASHConfiguration {

    /**
     * Creates a binary-phase only configuration.
     *
     * @param config
     * @param triggerTagEvent
     * @param storageStrategy
     * @param pruneWithLowerBounds
     * qparam anonymityPropertyPredictable
     * @return
     */
    public static FLASHConfiguration createBinaryPhaseConfiguration(FLASHPhaseConfiguration config,
                                                                    NodeAction triggerTagEvent,
                                                                    StorageStrategy storageStrategy,
                                                                    boolean pruneWithLowerBounds,
                                                                    boolean anonymityPropertyPredictable) {
        return new FLASHConfiguration(config, null, triggerTagEvent, storageStrategy, pruneWithLowerBounds, anonymityPropertyPredictable);
    }

    /**
     * Creates a linear-phase only configuration.
     *
     * @param config
     * @param triggerTagEvent
     * @param storageStrategy
     * @param pruneWithLowerBounds
     * qparam anonymityPropertyPredictable
     * @return
     */
    public static FLASHConfiguration createLinearPhaseConfiguration(FLASHPhaseConfiguration config,
                                                                    NodeAction triggerTagEvent,
                                                                    StorageStrategy storageStrategy,
                                                                    boolean pruneWithLowerBounds,
                                                                    boolean anonymityPropertyPredictable) {
        return new FLASHConfiguration(null, config, triggerTagEvent, storageStrategy, pruneWithLowerBounds, anonymityPropertyPredictable);
    }

    /**
     * Creates a two-phase configuration.
     *
     * @param binaryPhaseConfiguration
     * @param linearPhaseConfiguration
     * @param triggerTagEvent
     * @param storageStrategy
     * @param pruneWithLowerBounds
     * qparam anonymityPropertyPredictable
     * @return
     */
    public static FLASHConfiguration createTwoPhaseConfiguration(FLASHPhaseConfiguration binaryPhaseConfiguration,
                                                                 FLASHPhaseConfiguration linearPhaseConfiguration,
                                                                 NodeAction triggerTagEvent,
                                                                 StorageStrategy storageStrategy,
                                                                 boolean pruneWithLowerBounds,
                                                                 boolean anonymityPropertyPredictable) {
        return new FLASHConfiguration(binaryPhaseConfiguration,
                                      linearPhaseConfiguration,
                                      triggerTagEvent,
                                      storageStrategy,
                                      pruneWithLowerBounds,
                                      anonymityPropertyPredictable);
    }

    /** A configuration for the binary phase. */
    private final FLASHPhaseConfiguration binaryPhaseConfiguration;

    /** A configuration for the linear phase. */
    private final FLASHPhaseConfiguration linearPhaseConfiguration;

    /** Prune based on lower bounds from monotonic shares of metrics for information loss. */
    private final boolean                 pruneInsufficientUtility;

    /** A trigger controlling which transformations are snapshotted. */
    private final StorageStrategy         storageStrategy;

    /** A trigger firing when a tag event should be triggered. */
    private final NodeAction              triggerTagEvent;

    /** Determines whether the anonymity property is predictable */
    private final boolean                 anonymityPropertyPredictable;

    /**
     * Creates a new configuration for the FLASH algorithm.
     *
     * @param binaryPhaseConfiguration
     * @param linearPhaseConfiguration
     * @param triggerTagEvent
     * @param storageStrategy
     * @param pruneDueToLowerBound
     */
    private FLASHConfiguration(FLASHPhaseConfiguration binaryPhaseConfiguration,
                               FLASHPhaseConfiguration linearPhaseConfiguration,
                               NodeAction triggerTagEvent,
                               StorageStrategy storageStrategy,
                               boolean pruneDueToLowerBound,
                               boolean anonymityPropertyPredictable) {
        this.binaryPhaseConfiguration = binaryPhaseConfiguration;
        this.linearPhaseConfiguration = linearPhaseConfiguration;
        this.storageStrategy = storageStrategy;
        this.triggerTagEvent = triggerTagEvent;
        this.pruneInsufficientUtility = pruneDueToLowerBound;
        this.anonymityPropertyPredictable = anonymityPropertyPredictable;
    }

    /**
     * Getter.
     *
     * @return
     */
    public FLASHPhaseConfiguration getBinaryPhaseConfiguration() {
        return binaryPhaseConfiguration;
    }

    /**
     * Getter.
     *
     * @return
     */
    public FLASHPhaseConfiguration getLinearPhaseConfiguration() {
        return linearPhaseConfiguration;
    }

    /**
     * Getter: A trigger controlling which transformations are snapshotted.
     *
     * @return
     */
    public StorageStrategy getSnapshotStorageStrategy() {
        return storageStrategy;
    }

    /**
     * Getter: A trigger firing when a tag event should be triggered on the lattice.
     *
     * @return
     */
    public NodeAction getTriggerTagEvent() {
        return triggerTagEvent;
    }

    /**
     * Is a binary phase required.
     *
     * @return
     */
    public boolean isBinaryPhaseRequired() {
        return binaryPhaseConfiguration != null;
    }

    /**
     * Is a linear phase required.
     *
     * @return
     */
    public boolean isLinearPhaseRequired() {
        return linearPhaseConfiguration != null;
    }

    /**
     * Prune based on lower bounds from monotonic shares of metrics for information loss?.
     *
     * @return
     */
    public boolean isPruneInsufficientUtility() {
        return pruneInsufficientUtility;
    }

    /**
     * Returns whether or not the anonymity property is predictable
     * @return
     */
    public boolean isAnonymityPropertyPredicable() {
        return anonymityPropertyPredictable;
    }
}
