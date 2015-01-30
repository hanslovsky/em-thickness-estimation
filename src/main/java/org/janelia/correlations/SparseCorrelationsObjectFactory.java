package org.janelia.correlations;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Map.Entry;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.FloatType;
import net.imglib2.view.Views;

import org.janelia.correlations.CorrelationsObjectInterface.Meta;
import org.janelia.correlations.CrossCorrelation.CrossCorrelationRandomAccess;
import org.janelia.utility.SerializableConstantPair;
import org.janelia.utility.sampler.DenseXYSampler;
import org.janelia.utility.sampler.XYSampler;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 * @param <T>
 */
public class SparseCorrelationsObjectFactory < T extends RealType< T > > {
	
	private final RandomAccessibleInterval< T > images;
	private final XYSampler sampler;
	private final CrossCorrelation.TYPE type;
	private final int nThreads;
	
	
	/**
	 * @param images
	 */
	public SparseCorrelationsObjectFactory(final RandomAccessibleInterval<T> images, final XYSampler sampler, final CrossCorrelation.TYPE type, final int nThreads ) {
		super();
		this.images = images;
		this.sampler = sampler;
		this.type = type;
		this.nThreads = nThreads;
	}
	
	public SparseCorrelationsObjectFactory(final RandomAccessibleInterval<T> images, final XYSampler sampler, final int nThreads ) {
		this( images, sampler, CrossCorrelation.TYPE.STANDARD, nThreads );
	}
	
	public SparseCorrelationsObjectFactory(final RandomAccessibleInterval<T> images, final CrossCorrelation.TYPE type, final int nThreads ) {
		this(images, new DenseXYSampler( images.dimension(0), images.dimension(1) ), type, nThreads );
	}
	
	
	public SparseCorrelationsObjectFactory(final RandomAccessibleInterval<T> images, final int nThreads ) {
		this( images, new DenseXYSampler( images.dimension(0), images.dimension(1) ), nThreads );
	}


	public CorrelationsObjectInterface create( final long range, final long[] radius ) {

		
		final long stop = images.dimension( 2 ) - 1;
		
		final TreeMap<Long, Meta> metaMap = new TreeMap< Long, Meta > ();
		final TreeMap<SerializableConstantPair<Long, Long>, TreeMap<Long, double[]>> correlations = new TreeMap< SerializableConstantPair< Long, Long >, TreeMap< Long, double[] > >();
		
		final Iterator<SerializableConstantPair<Long, Long>> sampleIterator = sampler.iterator();
		int count = 0;
		while ( sampleIterator.hasNext() ) {
			ExecutorService threadpool = Executors.newFixedThreadPool( this.nThreads );
			ArrayList<Callable<Void>> callables = new ArrayList< Callable< Void > >();
			final SerializableConstantPair<Long, Long> xy = sampleIterator.next();
			// as we just created correlations, nothing present at XY yet; it is the user's responsibility to make sure, there's no duplicate coordinates in sampler
			final TreeMap<Long, double[]> correlationsAtXY = new TreeMap<Long, double[]>();
			correlations.put( xy, correlationsAtXY );
			final Long x = xy.getA();
			final Long y = xy.getB();
			
			for ( long zRef = 0; zRef <= stop; ++ zRef ) {
				final long lowerBound = Math.max( 0, zRef - range);
				final long upperBound = Math.min( stop, zRef + range );
				
				final Meta meta = new Meta();
				meta.zCoordinateMin = lowerBound;
				meta.zCoordinateMax = upperBound + 1;
				meta.zPosition      = zRef;
				final double[] correlationsAt = new double[ (int) (meta.zCoordinateMax - meta.zCoordinateMin) ];
				correlationsAtXY.put( zRef, correlationsAt);
			}
			
			for ( long zRef = 0; zRef <= stop; ++zRef ) {
				
				final long lowerBound = Math.max( 0, zRef - range);
				final long upperBound = Math.min( stop, zRef + range );
				
				final Meta meta = new Meta();
				meta.zCoordinateMin = lowerBound;
				meta.zCoordinateMax = upperBound + 1;
				meta.zPosition      = zRef;
				
				if ( count == 0 )
					metaMap.put( zRef, meta );
				
				// as we just created correlationsAtXY, nothing present at zRef yet
				final double[] correlationsAt = correlationsAtXY.get( zRef );
				
				for ( long z = zRef; z <= stop; ++z ) {
					
					final long lowerBoundOther         = Math.max( 0, z - range );
					final long upperBoundOther         = Math.min( stop, z + range );
					final double[] correlationsAtOther = correlationsAtXY.get( z );
							
					final int relativePosition1 = (int) ( z - lowerBound );
					final int relativePosition2 = (int) ( zRef - lowerBoundOther );
					
					if ( z == zRef ) {
						correlationsAt[ relativePosition1 ] = 1.0;
						continue;
					}
					
					final long zf    = z;
					final long zReff = zRef;
					
					
					callables.add( new Callable<Void>() {

						@Override
						public Void call() throws Exception {
							final CrossCorrelation<T, T, FloatType > cc = new CrossCorrelation< T, T, FloatType >(
									Views.hyperSlice( images, 2, zf ),
									Views.hyperSlice( images, 2, zReff ),
									radius,
									type,
									new FloatType() );
							final CrossCorrelationRandomAccess ra = cc.randomAccess();
							ra.setPosition( new long[] { x, y } );
							double val = ra.get().getRealDouble();
							correlationsAt[ relativePosition1 ] = val;
							correlationsAtOther[ relativePosition2 ] = val;
							return null;
						}
					});
					
				}
				
			}
			++count;
			try {
				threadpool.invokeAll( callables );
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
		
		final SparseCorrelationsObject sco = new SparseCorrelationsObject();

		for ( final Entry<SerializableConstantPair<Long, Long>, TreeMap<Long, double[]>> entry : correlations.entrySet() ) 
		{
			final Long x = entry.getKey().getA();
			final Long y = entry.getKey().getB();
			for ( final Entry<Long, double[]> corrEntry : entry.getValue().entrySet() ) {
				final Long z = corrEntry.getKey();
				if ( x == 0 && y == 0 )
					sco.addCorrelationsAt( x, y, z, corrEntry.getValue(), metaMap.get( z ) );
				else
					sco.addCorrelationsAt( x, y, z, corrEntry.getValue() );
			}
		}
		
		return sco;
	}
	
	
	public static < T extends RealType< T > > SparseCorrelationsObject fromMatrix( final RandomAccessibleInterval<T> matrix, final long x, final long y, final int comparisonRange ) {
		assert matrix.numDimensions() == 2;
		assert matrix.dimension( 0 ) == matrix.dimension( 1 );
		assert comparisonRange <= matrix.dimension( 0 );
		final TreeMap<Long, Meta> metaMap           = new TreeMap< Long, Meta >();
		final RandomAccess<T> ra = matrix.randomAccess();
		
		final SparseCorrelationsObject sco = new SparseCorrelationsObject();
		
		for ( int z = 0; z < matrix.dimension( 0 ); ++z ) {
			ra.setPosition( z, 0 );
			final int lower = Math.max( z - comparisonRange, 0 );
			final int upper = Math.min( z + comparisonRange, (int)matrix.dimension( 0 ) );
			
			final Meta meta = new Meta();
			meta.zCoordinateMin = lower;
			meta.zCoordinateMax = upper;
			meta.zPosition      = z;
			metaMap.put( (long) z, meta );
			final double[] c = new double[ upper - lower ];
			for ( int l = lower; l < upper; ++l ) {
				ra.setPosition( l, 1 );
				c[ l - lower ] = ra.get().getRealDouble();
			}
			sco.addCorrelationsAt( x, y, z, c, meta );
		}
		
		return sco;
	}
	
}
