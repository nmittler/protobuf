package com.google.protobuf;


import com.google.common.cache.Cache;
import com.google.common.cache.NettyCache;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.ByteBufUtil;
import io.netty.buffer.PooledByteBufAllocator;

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;

/**
 * Created by nathanmittler on 10/27/15.
 */
@State(Scope.Benchmark)
@Fork(1)
public class PoolingBenchmarks {

  public enum ReleaseType {
    MANUAL,
    GC
  }

  @Param
  private ReleaseType releaseType = ReleaseType.MANUAL;

  @Param({"false", "true"})
  private boolean directBuffers;

  private ByteBufAllocator allocator;
  private BufferProcessor processor;
  private byte[] scratch = new byte[100];

  interface BufferProcessor {
    void processBuffer(ByteBuf buffer) throws Exception;
  }

  final class ManualReleaseProcessor implements BufferProcessor {
    @Override
    public void processBuffer(ByteBuf buffer) throws Exception {
      ByteString bs = UnsafeByteStrings.wrap(buffer.nioBuffer());
      doWork(bs);
      buffer.release();
    }
  }

  final class GcReleaseProcessor implements BufferProcessor {
    private final Cache<ByteString,ByteBuf> evictionCache = NettyCache.newCache();

    @Override
    public void processBuffer(ByteBuf buffer) throws Exception {
      ByteString bs = UnsafeByteStrings.wrap(buffer.nioBuffer());
      evictionCache.put(bs, buffer);
      doWork(bs);
    }
  }

  @Setup(Level.Trial)
  public void setup() {
    int numHeapArenas = PooledByteBufAllocator.DEFAULT.numHeapArenas();
    int numDirectArenas = PooledByteBufAllocator.DEFAULT.numDirectArenas();
    int pageSize = 8192;
    int maxOrder = 11;
    int tinyCacheSize = PooledByteBufAllocator.DEFAULT.tinyCacheSize();
    int smallCacheSize = PooledByteBufAllocator.DEFAULT.smallCacheSize();
    int normalCacheSize = PooledByteBufAllocator.DEFAULT.normalCacheSize();
    allocator = new PooledByteBufAllocator(directBuffers, numHeapArenas, numDirectArenas, pageSize,
        maxOrder, tinyCacheSize, smallCacheSize, normalCacheSize);

    switch(releaseType) {
      case MANUAL:
        processor = new ManualReleaseProcessor();
        break;
      case GC:
        processor = new GcReleaseProcessor();
        break;
    }
  }

  @Benchmark
  public void test() throws Exception {
    ByteBuf buf = allocator.buffer();
    ByteBufUtil.writeUtf8(buf, "hello world");
    processor.processBuffer(buf);
  }

  private void doWork(ByteString bs) {
    // Just copy the bytes to the scratch buffer.
    bs.copyTo(scratch, 0);
  }
}