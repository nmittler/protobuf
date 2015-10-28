package com.google.common.cache;

import com.google.common.base.Equivalence;
import com.google.protobuf.ByteString;

import io.netty.buffer.ByteBuf;

/**
 * Provides an eviction cache that releases Netty ByteBufs. This is a hack due to the fact
 * that Guava's CacheBuilder does not make {@code keyEquilalence} public.
 */
public final class NettyCache {
  private NettyCache() {
  }

  public static Cache<ByteString, ByteBuf> newCache() {
    return CacheBuilder.newBuilder()
        .weakKeys()
        .keyEquivalence(Equivalence.identity())
        .removalListener(
            new RemovalListener<ByteString, ByteBuf>() {
              @Override
              public void onRemoval(RemovalNotification<ByteString, ByteBuf> removed) {
                removed.getValue().release();
              }
            }).build();
  }
}
