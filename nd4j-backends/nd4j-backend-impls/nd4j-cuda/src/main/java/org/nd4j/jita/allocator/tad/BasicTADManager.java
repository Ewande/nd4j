package org.nd4j.jita.allocator.tad;

import org.apache.commons.math3.util.Pair;
import org.bytedeco.javacpp.IntPointer;
import org.bytedeco.javacpp.Pointer;
import org.nd4j.jita.allocator.impl.AtomicAllocator;
import org.nd4j.linalg.api.buffer.DataBuffer;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.cache.TADManager;
import org.nd4j.linalg.jcublas.buffer.AddressRetriever;
import org.nd4j.linalg.jcublas.buffer.CudaDoubleDataBuffer;
import org.nd4j.linalg.jcublas.buffer.CudaIntDataBuffer;
import org.nd4j.linalg.jcublas.buffer.CudaLongDataBuffer;
import org.nd4j.nativeblas.LongPointerWrapper;
import org.nd4j.nativeblas.NativeOps;
import org.nd4j.nativeblas.NativeOpsHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author raver119@gmail.com
 */
public class BasicTADManager implements TADManager {
    protected NativeOps nativeOps = NativeOpsHolder.getInstance().getDeviceNativeOps();
    private static Logger logger = LoggerFactory.getLogger(BasicTADManager.class);
    protected AtomicLong bytes = new AtomicLong(0);

    @Override
    public Pair<DataBuffer, DataBuffer> getTADOnlyShapeInfo(INDArray array, int[] dimension) {
        if (dimension != null && dimension.length > 1)
            Arrays.sort(dimension);

        if (dimension == null)
            dimension = new int[] {Integer.MAX_VALUE};

        boolean isScalar = dimension == null || (dimension.length == 1 && dimension[0] == Integer.MAX_VALUE);

        // FIXME: this is fast triage, remove it later
        int targetRank = isScalar ? 2 : array.rank(); //dimensionLength <= 1 ? 2 : dimensionLength;
        long offsetLength = 0;
        long tadLength = 1;

        if(!isScalar)
            for (int i = 0; i < dimension.length; i++) {
                tadLength *= array.shape()[dimension[i]];
            }

        if(!isScalar)
            offsetLength = array.lengthLong() / tadLength;
        else
            offsetLength = 1;
        //     logger.info("Original shape info before TAD: {}", array.shapeInfoDataBuffer());
        //    logger.info("dimension: {}, tadLength: {}, offsetLength for TAD: {}", Arrays.toString(dimension),tadLength, offsetLength);

        DataBuffer outputBuffer = new CudaIntDataBuffer(targetRank * 2 + 4);
        DataBuffer offsetsBuffer = new CudaLongDataBuffer(offsetLength);

        AtomicAllocator.getInstance().getAllocationPoint(outputBuffer).tickHostWrite();
        AtomicAllocator.getInstance().getAllocationPoint(offsetsBuffer).tickHostWrite();

        DataBuffer dimensionBuffer = AtomicAllocator.getInstance().getConstantBuffer(dimension);
        Pointer dimensionPointer = AtomicAllocator.getInstance().getHostPointer(dimensionBuffer);

        Pointer xShapeInfo = AddressRetriever.retrieveHostPointer(array.shapeInfoDataBuffer());
        Pointer targetPointer = AddressRetriever.retrieveHostPointer(outputBuffer);
        Pointer offsetsPointer = AddressRetriever.retrieveHostPointer(offsetsBuffer);
        if(!isScalar)
            nativeOps.tadOnlyShapeInfo((IntPointer) xShapeInfo, (IntPointer) dimensionPointer, dimension.length,
                    (IntPointer) targetPointer, new LongPointerWrapper(offsetsPointer));

        else  {
            outputBuffer.put(0,2);
            outputBuffer.put(1,1);
            outputBuffer.put(2,1);
            outputBuffer.put(3,1);
            outputBuffer.put(4,1);
            outputBuffer.put(5,0);
            outputBuffer.put(6,0);
            outputBuffer.put(7,99);

        }

        AtomicAllocator.getInstance().getAllocationPoint(outputBuffer).tickHostWrite();
        AtomicAllocator.getInstance().getAllocationPoint(offsetsBuffer).tickHostWrite();

        //   logger.info("TAD shapeInfo after construction: {}", Arrays.toString(TadDescriptor.dataBufferToArray(outputBuffer)));
        // now we need to copy this buffer to either device global memory or device cache

        return new Pair<>(outputBuffer, offsetsBuffer);

    }

    /**
     * This method removes all cached shape buffers
     */
    @Override
    public void purgeBuffers() {
        // no-op
    }

    @Override
    public long getCachedBytes() {
        return bytes.get();
    }
}
