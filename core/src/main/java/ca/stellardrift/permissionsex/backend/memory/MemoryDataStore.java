/*
 * PermissionsEx
 * Copyright (C) zml and PermissionsEx contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.stellardrift.permissionsex.backend.memory;

import ca.stellardrift.permissionsex.backend.AbstractDataStore;
import ca.stellardrift.permissionsex.config.FilePermissionsExConfiguration;
import ca.stellardrift.permissionsex.datastore.DataStore;
import ca.stellardrift.permissionsex.datastore.DataStoreFactory;
import ca.stellardrift.permissionsex.datastore.StoreProperties;
import ca.stellardrift.permissionsex.context.ContextValue;
import ca.stellardrift.permissionsex.context.ContextInheritance;
import ca.stellardrift.permissionsex.exception.PermissionsLoadingException;
import ca.stellardrift.permissionsex.subject.ImmutableSubjectData;
import ca.stellardrift.permissionsex.rank.FixedRankLadder;
import ca.stellardrift.permissionsex.rank.RankLadder;
import ca.stellardrift.permissionsex.util.GuavaCollectors;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import org.spongepowered.configurate.BasicConfigurationNode;
import org.spongepowered.configurate.objectmapping.ConfigSerializable;
import org.spongepowered.configurate.objectmapping.meta.Comment;
import org.spongepowered.configurate.objectmapping.meta.Setting;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A data store backed entirely in memory
 */
public class MemoryDataStore extends AbstractDataStore<MemoryDataStore, MemoryDataStore.Config> {
    @ConfigSerializable
    static class Config {
        @Setting
        @Comment("Whether or not this data store will store subjects being set")
        boolean track = true;
    }

    @AutoService(DataStoreFactory.class)
    public static final class Factory extends AbstractDataStore.Factory<MemoryDataStore, Config> {
        static final String TYPE = "memory";

        public Factory() {
            super(TYPE, Config.class, MemoryDataStore::new);
        }
    }


    private final ConcurrentMap<Map.Entry<String, String>, ImmutableSubjectData> data = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, RankLadder> rankLadders = new ConcurrentHashMap<>();
    private volatile ContextInheritance inheritance = new MemoryContextInheritance();

    public static MemoryDataStore create(final String identifier) {
        try {
            return (MemoryDataStore) DataStoreFactory.forType(Factory.TYPE).create(identifier, BasicConfigurationNode.root(FilePermissionsExConfiguration.PEX_OPTIONS));
        } catch (final PermissionsLoadingException ex) {
            // Not possible to have loading errors when we're not loading anything
            throw new RuntimeException(ex);
        }
    }

    public MemoryDataStore(final StoreProperties<Config> properties) {
        super(properties);
    }

    @Override
    protected boolean initializeInternal() {
        return false; // we never have any starting data
    }

    @Override
    public void close() {
    }

    @Override
    public CompletableFuture<ImmutableSubjectData> getDataInternal(String type, String identifier) {
        final Map.Entry<String, String> key = Maps.immutableEntry(type, identifier);
        ImmutableSubjectData ret = data.get(key);
        if (ret == null) {
            ret = new MemorySubjectData();
            if (config().track) {
                final ImmutableSubjectData existingData = data.putIfAbsent(key, ret);
                if (existingData != null) {
                    ret = existingData;
                }
            }
        }
        return completedFuture(ret);
    }

    @Override
    public CompletableFuture<ImmutableSubjectData> setDataInternal(String type, String identifier, ImmutableSubjectData data) {
        if (config().track) {
            this.data.put(Maps.immutableEntry(type, identifier), data);
        }
        return completedFuture(data);
    }

    @Override
    protected CompletableFuture<RankLadder> getRankLadderInternal(String name) {
        RankLadder ladder = rankLadders.get(name.toLowerCase());
        if (ladder == null) {
            ladder = new FixedRankLadder(name, ImmutableList.<Map.Entry<String, String>>of());
        }
        return completedFuture(ladder);
    }

    @Override
    protected CompletableFuture<RankLadder> setRankLadderInternal(String ladder, RankLadder newLadder) {
        this.rankLadders.put(ladder, newLadder);
        return completedFuture(newLadder);
    }

    private <T> CompletableFuture<T> completedFuture(T i) {
        return CompletableFuture.supplyAsync(() -> i, getManager().getAsyncExecutor());
    }

    @Override
    public CompletableFuture<Boolean> isRegistered(String type, String identifier) {
        return completedFuture(data.containsKey(Maps.immutableEntry(type, identifier)));
    }

    @Override
    public Set<String> getAllIdentifiers(final String type) {
        return data.keySet().stream()
                .filter(inp -> inp.getKey().equals(type))
                .map(Map.Entry::getValue)
                .collect(GuavaCollectors.toImmutableSet());
    }

    @Override
    public Set<String> getRegisteredTypes() {
        return ImmutableSet.copyOf(Iterables.transform(data.keySet(), Map.Entry::getKey));
    }

    @Override
    public CompletableFuture<Set<String>> getDefinedContextKeys() {
        return CompletableFuture.completedFuture(data.values().stream()
                .flatMap(data -> data.getActiveContexts().stream())
                .flatMap(Collection::stream)
                .map(ContextValue::key)
                .collect(Collectors.toSet()));
    }

    @Override
    public Iterable<Map.Entry<Map.Entry<String, String>, ImmutableSubjectData>> getAll() {
        return Iterables.unmodifiableIterable(data.entrySet());
    }

    @Override
    public Iterable<String> getAllRankLadders() {
        return ImmutableSet.copyOf(rankLadders.keySet());
    }

    @Override
    public CompletableFuture<Boolean> hasRankLadder(String ladder) {
        return completedFuture(rankLadders.containsKey(ladder.toLowerCase()));
    }

    @Override
    public CompletableFuture<ContextInheritance> getContextInheritanceInternal() {
        return completedFuture(this.inheritance);
    }

    @Override
    public CompletableFuture<ContextInheritance> setContextInheritanceInternal(ContextInheritance inheritance) {
        this.inheritance = inheritance;
        return completedFuture(this.inheritance);
    }

    @Override
    protected <T> T performBulkOperationSync(Function<DataStore, T> function) {
        return function.apply(this);
    }
}
