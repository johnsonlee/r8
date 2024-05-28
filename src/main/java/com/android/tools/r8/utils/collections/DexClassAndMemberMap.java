// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.collections;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.graph.DexClassAndMember;
import com.android.tools.r8.utils.BiForEachable;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.TraversalContinuation;
import com.android.tools.r8.utils.TriPredicate;
import com.google.common.base.Equivalence.Wrapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

public abstract class DexClassAndMemberMap<K extends DexClassAndMember<?, ?>, V>
    implements BiForEachable<K, V>, Map<K, V> {

  private final Map<Wrapper<K>, V> backing;

  DexClassAndMemberMap(Supplier<Map<Wrapper<K>, V>> backingFactory) {
    this.backing = backingFactory.get();
  }

  DexClassAndMemberMap(Map<Wrapper<K>, V> backing) {
    this.backing = backing;
  }

  @Override
  public void clear() {
    backing.clear();
  }

  public V compute(K member, BiFunction<K, V, V> fn) {
    return backing.compute(wrap(member), (key, value) -> fn.apply(member, value));
  }

  public V computeIfAbsent(K member, Function<K, V> fn) {
    return computeIfAbsent(wrap(member), fn);
  }

  public V computeIfAbsent(Wrapper<K> wrapper, Function<K, V> fn) {
    return backing.computeIfAbsent(wrapper, key -> fn.apply(key.get()));
  }

  @SuppressWarnings("unchecked")
  @Override
  public boolean containsKey(Object key) {
    return containsKey((K) key);
  }

  public boolean containsKey(K member) {
    return backing.containsKey(wrap(member));
  }

  @Override
  public boolean containsValue(Object value) {
    throw new Unimplemented();
  }

  @Override
  public Set<Entry<K, V>> entrySet() {
    throw new Unimplemented();
  }

  @Override
  public void forEach(BiConsumer<? super K, ? super V> consumer) {
    backing.forEach((wrapper, value) -> consumer.accept(wrapper.get(), value));
  }

  public void forEachKey(Consumer<K> consumer) {
    backing.keySet().forEach(wrapper -> consumer.accept(wrapper.get()));
  }

  public void forEachValue(Consumer<V> consumer) {
    backing.values().forEach(consumer);
  }

  @SuppressWarnings("unchecked")
  @Override
  public V get(Object key) {
    return get((K) key);
  }

  public V get(K member) {
    return backing.get(wrap(member));
  }

  public V get(Wrapper<K> wrapper) {
    return backing.get(wrapper);
  }

  public List<K> getKeys() {
    List<K> keys = new ArrayList<>(size());
    backing.keySet().forEach(key -> keys.add(key.get()));
    return keys;
  }

  public List<K> getKeysSorted() {
    return ListUtils.sort(getKeys(), (x, y) -> x.getReference().compareTo(y.getReference()));
  }

  @SuppressWarnings("unchecked")
  @Override
  public V getOrDefault(Object key, V defaultValue) {
    return getOrDefault((K) key, defaultValue);
  }

  public V getOrDefault(K member, V defaultValue) {
    return backing.getOrDefault(wrap(member), defaultValue);
  }

  public V getOrDefault(K member, Supplier<V> defaultValue) {
    V value = backing.get(wrap(member));
    return value != null ? value : defaultValue.get();
  }

  @Override
  public boolean isEmpty() {
    return backing.isEmpty();
  }

  @Override
  public Set<K> keySet() {
    throw new Unimplemented();
  }

  @Override
  public V put(K member, V value) {
    Wrapper<K> wrapper = wrap(member);
    return backing.put(wrapper, value);
  }

  @Override
  public void putAll(Map<? extends K, ? extends V> m) {
    throw new Unimplemented();
  }

  @SuppressWarnings("unchecked")
  @Override
  public V remove(Object key) {
    return remove((K) key);
  }

  public V remove(K member) {
    return backing.remove(wrap(member));
  }

  public boolean removeIf(BiPredicate<K, V> predicate) {
    return backing
        .entrySet()
        .removeIf(entry -> predicate.test(entry.getKey().get(), entry.getValue()));
  }

  public boolean removeIf(TriPredicate<K, V, Entry<?, V>> predicate) {
    return backing
        .entrySet()
        .removeIf(entry -> predicate.test(entry.getKey().get(), entry.getValue(), entry));
  }

  public V removeOrDefault(K member, V defaultValue) {
    V value = remove(member);
    return value != null ? value : defaultValue;
  }

  @Override
  public int size() {
    return backing.size();
  }

  public Stream<K> streamKeys() {
    return streamWrappedKeys().map(Wrapper::get);
  }

  public Stream<Wrapper<K>> streamWrappedKeys() {
    return backing.keySet().stream();
  }

  public <TB, TC> TraversalContinuation<TB, TC> traverse(
      BiFunction<K, V, TraversalContinuation<TB, TC>> fn) {
    for (Entry<Wrapper<K>, V> entry : backing.entrySet()) {
      TraversalContinuation<TB, TC> traversalContinuation =
          fn.apply(entry.getKey().get(), entry.getValue());
      if (traversalContinuation.shouldBreak()) {
        return traversalContinuation;
      }
    }
    return TraversalContinuation.doContinue();
  }

  @Override
  public Collection<V> values() {
    return backing.values();
  }

  protected abstract Wrapper<K> wrap(K member);
}
