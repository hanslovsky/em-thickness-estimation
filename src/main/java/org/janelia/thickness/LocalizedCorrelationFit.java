package org.janelia.thickness;

import java.util.ArrayList;

import mpicbg.models.IllDefinedDataPointsException;
import mpicbg.models.Model;
import mpicbg.models.NotEnoughDataPointsException;
import mpicbg.models.Point;
import mpicbg.models.PointMatch;
import net.imglib2.Cursor;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.RealRandomAccess;
import net.imglib2.RealRandomAccessible;
import net.imglib2.img.array.ArrayImg;
import net.imglib2.img.array.ArrayImgs;
import net.imglib2.img.basictypeaccess.array.DoubleArray;
import net.imglib2.img.list.ListCursor;
import net.imglib2.img.list.ListImg;
import net.imglib2.interpolation.randomaccess.NLinearInterpolatorFactory;
import net.imglib2.realtransform.InverseRealTransform;
import net.imglib2.realtransform.RealTransformRealRandomAccessible;
import net.imglib2.realtransform.RealViews;
import net.imglib2.type.numeric.RealType;
import net.imglib2.type.numeric.real.DoubleType;
import net.imglib2.view.Views;

import org.janelia.thickness.cluster.Categorizer;
import org.janelia.thickness.cluster.RangedCategorizer;
import org.janelia.thickness.lut.AbstractLUTRealTransform;
import org.janelia.thickness.lut.LUTRealTransform;

/**
 * @author Philipp Hanslovsky <hanslovskyp@janelia.hhmi.org>
 *
 */
public class LocalizedCorrelationFit {

	private static final double[] ONE_DIMENSION_ZERO_POSITION = new double[]{ 0.0 };

	public static < T extends RealType< T >, M extends Model< M > > void estimateFromMatrix( final RandomAccessibleInterval< T > correlations,
			final double[] coordinates,
			final double[] weights,
			final double[] multipliers,
			final AbstractLUTRealTransform transform,
			final int range,
			final int windowRange,
			final M correlationFitModel,
			final ListImg< double[] > localFits) throws NotEnoughDataPointsException, IllDefinedDataPointsException {

		final RangedCategorizer categorizer = new RangedCategorizer( windowRange );
		categorizer.generateLabels( windowRange );
		estimateFromMatrix(correlations, coordinates, weights, multipliers, transform, range, correlationFitModel, categorizer, localFits);
	}

	public static < T extends RealType< T >, M extends Model< M > > void estimateFromMatrix( final RandomAccessibleInterval< T > correlations,
			final double[] coordinates,
			final double[] weights,
			final double[] multipliers,
			final AbstractLUTRealTransform transform,
			final int range,
			final M correlationFitModel,
			final Categorizer categorizer,
			final ListImg< double[] > localFits) throws NotEnoughDataPointsException, IllDefinedDataPointsException {

		assert localFits.numDimensions() == 2;
		assert localFits.dimension( 1 )  == coordinates.length;
//		assert localFits.firstElement().length == range;

		final double[][] assignments = categorizer.getLabels( coordinates );
		final int numberOfModels = assignments[0].length;

		final ArrayList< ArrayList< ArrayList< PointMatch > > > samples = new ArrayList< ArrayList< ArrayList< PointMatch > > >();
		for ( int s = 0; s < numberOfModels; ++s ) {
			final ArrayList<ArrayList<PointMatch>> al = new ArrayList< ArrayList< PointMatch> >();
			for ( int k = 0; k <= range; ++k ) {
				al.add( new ArrayList<PointMatch>() );
			}
			samples.add( al );
		}

		final T dummy = correlations.randomAccess().get().copy();
		dummy.setReal( Double.NaN );
		final RealRandomAccessible< T > source = Views.interpolate( Views.extendValue( correlations, dummy ), new NLinearInterpolatorFactory< T >() );

		final RealTransformRealRandomAccessible< T, InverseRealTransform> source2 = RealViews.transformReal(source, transform);
		final LUTRealTransform tf1d = new LUTRealTransform( coordinates, 1, 1);
		final RealTransformRealRandomAccessible<DoubleType, InverseRealTransform> multipliersInterpolatedTransformed =
				RealViews.transformReal( Views.interpolate( Views.extendValue( ArrayImgs.doubles( multipliers, multipliers.length ), new DoubleType( Double.NaN ) ), new NLinearInterpolatorFactory<DoubleType>()),
				tf1d );

		final RealRandomAccess< T > access  = source2.realRandomAccess();
		final RealRandomAccess< T > access2 = source2.realRandomAccess();

		final RealRandomAccess< DoubleType > m1 = multipliersInterpolatedTransformed.realRandomAccess();
		final RealRandomAccess< DoubleType > m2 = multipliersInterpolatedTransformed.realRandomAccess();



		for ( int i = 0; i < correlations.dimension( 1 ); ++i ) {

			access.setPosition( i, 1 );
			access.setPosition( i, 0 );

			transform.apply(access, access);
			access2.setPosition(access);

			m1.setPosition( i, 0 );
			tf1d.apply( m1, m1 );
			m2.setPosition( m1 );
			final double mref = m1.get().get();

			final double[] currentAssignment = assignments[i];

			for ( int k = 0; k <= range; ++k, access.fwd( 0 ), access2.bck( 0 ), m1.fwd( 0 ), m2.bck( 0 ) ) {

//				if ( i < coordinates.length - 1 && coordinates[i] + k < coordinates[ i + 1 ] )
//					continue;
//
//				if ( i > 0 && coordinates[i] - k > coordinates[ i - 1 ] )
//					continue;

				final double a1 = access.get().getRealDouble();
				final double a2 = access2.get().getRealDouble();

				if ( ( ! Double.isNaN( a1 ) ) && ( a1 > 0.0 ) )
				{
					final int index = i + k;
					if ( index < weights.length ) {
						final double w1 = weights[ index ]; // replace 1.0 by real weight, as soon as weight calculation has become clear
						for ( int modelIndex = 0; modelIndex < currentAssignment.length; ++modelIndex )
							samples.get( modelIndex ).get( k ).add( new PointMatch( new Point( ONE_DIMENSION_ZERO_POSITION ), new Point( new double[]{ a1*mref*m1.get().get() } ), w1 ) );
					}
				}

				if ( ( ! Double.isNaN( a2 ) ) && ( a2 > 0.0 ) )
				{
					final int index = i - k;
					if ( index > 0 ) {
						final double w2 = weights[ index ]; // replace 1.0 by real weight, as soon as weight calculation has become clear
						for ( int modelIndex = 0; modelIndex < currentAssignment.length; ++modelIndex )
							samples.get( modelIndex ).get( k ).add( new PointMatch( new Point( ONE_DIMENSION_ZERO_POSITION ), new Point( new double[]{ a2*mref*m2.get().get() } ), w2 ) );
					}
				}
			}
		}

		final ArrayImg<DoubleType, DoubleArray> fits = ArrayImgs.doubles( numberOfModels, range );
		for ( int m = 0; m < numberOfModels; ++m ) {
			final Cursor<DoubleType> cursor = Views.flatIterable( Views.hyperSlice( fits, 0, m ) ).cursor();
			final ArrayList<ArrayList<PointMatch>> points = samples.get( m );
			/* TODO inverts because LUTRealTransform can only increasing */
			cursor.next().set( -1.0 );
			for ( int k = 1; cursor.hasNext(); ++k ) {
				correlationFitModel.fit( points.get( k ) );
				/* TODO inverts because LUTRealTransform can only increasing */
				cursor.next().set( -correlationFitModel.apply( ONE_DIMENSION_ZERO_POSITION )[0] );
			}
		}

		final ListCursor<double[]> c = localFits.cursor();
		while( c.hasNext() ) {
			c.fwd();
			final double[] arr = c.get();
			for (int j = 0; j < arr.length; j++) {
				arr[j] = 0.0;
			}
			final int pos = c.getIntPosition( 0 );
			final double[] localAssignments = assignments[pos];
			for ( int m = 0; m < numberOfModels; ++m ) {
				final Cursor<DoubleType> cursor = Views.flatIterable( Views.hyperSlice( fits, 0, m ) ).cursor();
				for ( int k = 0; cursor.hasNext(); ++k ) {
					arr[k] += cursor.next().get() * localAssignments[m];
				}
			}
		}

	}


	public static < T extends RealType< T >, M extends Model< M > > void estimateFromMatrix( final RandomAccessibleInterval< T > correlations,
			final double[] coordinates,
			final double[] weights,
			final double[] multipliers,
			final AbstractLUTRealTransform transform,
			final int range,
			final M correlationFitModel,
			final ListImg< double[] > localFits) throws NotEnoughDataPointsException, IllDefinedDataPointsException {
		final int windowRange = coordinates.length;
		estimateFromMatrix( correlations, coordinates, weights, multipliers, transform, range, windowRange, correlationFitModel, localFits );
	}


	public <M extends Model< M > > void estimateFromStrip( final RandomAccessibleInterval< DoubleType > transformedCorrelations,
			final RandomAccessibleInterval< DoubleType > multipliersTransformed,
			final double[] weights,
			final int range,
			final int windowRange,
			final M correlationFitModel,
			final ListImg< double[] > localFits ) throws NotEnoughDataPointsException, IllDefinedDataPointsException {

		final RangedCategorizer categorizer = new RangedCategorizer( windowRange );
		categorizer.generateLabels( windowRange );
		this.estimateFromStrip( transformedCorrelations, multipliersTransformed, weights, windowRange, correlationFitModel, categorizer, localFits);
	}


	public <M extends Model< M > > void estimateFromStrip( final RandomAccessibleInterval< DoubleType > transformedCorrelations,
			final RandomAccessibleInterval< DoubleType > multipliersTransformed,
			final double[] weights,
			final int range,
			final M correlationFitModel,
			final Categorizer categorizer,
			final ListImg< double[] > localFits ) throws NotEnoughDataPointsException, IllDefinedDataPointsException {

		final int numPoints = 2*range + 1;

		assert localFits.numDimensions()   == 2;
		assert localFits.dimension( 1 )    == transformedCorrelations.dimension( 1 );
		assert multipliersTransformed.numDimensions() == 1;
		assert multipliersTransformed.dimension( 0 )  == transformedCorrelations.dimension( 1	);
		assert transformedCorrelations.dimension( 0 ) == numPoints;

		final int numHalfPoints = numPoints / 2;

		final double[][] assignments = categorizer.getLabels( transformedCorrelations );
		final int numberOfModels = assignments[0].length;

		final ArrayList< ArrayList< ArrayList< PointMatch > > > samples = new ArrayList< ArrayList< ArrayList< PointMatch > > >();
		for ( int s = 0; s < numberOfModels; ++s ) {
			final ArrayList<ArrayList<PointMatch>> al = new ArrayList< ArrayList< PointMatch> >();
			for ( int k = 0; k <= range; ++k ) {
				al.add( new ArrayList<PointMatch>() );
			}
			samples.add( al );
		}

		final RandomAccess< DoubleType > access1 = transformedCorrelations.randomAccess();
		final RandomAccess< DoubleType > access2 = transformedCorrelations.randomAccess();

		final RandomAccess< DoubleType > m1 = multipliersTransformed.randomAccess();
		final RandomAccess< DoubleType > m2 = multipliersTransformed.randomAccess();



		for ( int i = 0; i < transformedCorrelations.dimension( 1 ); ++i ) {

			access1.setPosition( i, 1 );
			access1.setPosition( numHalfPoints, 0 );

			access2.setPosition( access1 );

			m1.setPosition( i, 0 );
			m2.setPosition( m1 );

			final double mref = m1.get().get();

			final double[] currentAssignment = assignments[i];

			for ( int k = 0; k <= range; ++k, access1.fwd( 0 ), access2.bck( 0 ), m1.fwd( 0 ), m2.bck( 0 ) ) {

//				if ( i < coordinates.length - 1 && coordinates[i] + k < coordinates[ i + 1 ] )
//					continue;
//
//				if ( i > 0 && coordinates[i] - k > coordinates[ i - 1 ] )
//					continue;

				final double a1 = access1.get().get();
				final double a2 = access2.get().get();

				if ( ! Double.isNaN( a1 ) )
				{
					final int index = i + k;
					if ( index < weights.length ) {
						final double w1 = weights[ index ]; // replace 1.0 by real weight, as soon as weight calculation has become clear
						for ( int modelIndex = 0; modelIndex < currentAssignment.length; ++modelIndex )
							samples.get( modelIndex ).get( k ).add( new PointMatch( new Point( ONE_DIMENSION_ZERO_POSITION ), new Point( new double[]{ a1*mref*m1.get().get() } ), w1 ) );
					}
				}

				if ( ( ! Double.isNaN( a2 ) ) )
				{
					final int index = i - k;
					if ( index > 0 ) {
						final double w2 = weights[ index ]; // replace 1.0 by real weight, as soon as weight calculation has become clear
						for ( int modelIndex = 0; modelIndex < currentAssignment.length; ++modelIndex )
							samples.get( modelIndex ).get( k ).add( new PointMatch( new Point( ONE_DIMENSION_ZERO_POSITION ), new Point( new double[]{ a2*mref*m2.get().get() } ), w2 ) );
					}
				}
			}
		}

		final ArrayImg<DoubleType, DoubleArray> fits = ArrayImgs.doubles( numberOfModels, range );
		for ( int m = 0; m < numberOfModels; ++m ) {
			final Cursor<DoubleType> cursor = Views.flatIterable( Views.hyperSlice( fits, 0, m ) ).cursor();
			final ArrayList<ArrayList<PointMatch>> points = samples.get( m );
			/* TODO inverts because LUTRealTransform can only increasing */
			cursor.next().set( -1.0 );
			for ( int k = 1; cursor.hasNext(); ++k ) {
				correlationFitModel.fit( points.get( k ) );
				/* TODO inverts because LUTRealTransform can only increasing */
				cursor.next().set( -correlationFitModel.apply( ONE_DIMENSION_ZERO_POSITION )[0] );
			}
		}

		final ListCursor<double[]> c = localFits.cursor();
		while( c.hasNext() ) {
			c.fwd();
			final double[] arr = c.get();
			for (int j = 0; j < arr.length; j++) {
				arr[j] = 0.0;
			}
			final int pos = c.getIntPosition( 0 );
			final double[] localAssignments = assignments[pos];
			for ( int m = 0; m < numberOfModels; ++m ) {
				final Cursor<DoubleType> cursor = Views.flatIterable( Views.hyperSlice( fits, 0, m ) ).cursor();
				for ( int k = 0; cursor.hasNext(); ++k ) {
					arr[k] += cursor.next().get() * localAssignments[m];
				}
			}
		}

	}

}
