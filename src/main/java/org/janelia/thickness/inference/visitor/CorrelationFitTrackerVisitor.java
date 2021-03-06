/**
 * 
 */
package org.janelia.thickness.inference.visitor;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;

import net.imglib2.RandomAccessibleInterval;
import net.imglib2.type.numeric.RealType;
import net.imglib2.view.Views;

/**
 * @author hanslovskyp
 *
 */
public class CorrelationFitTrackerVisitor extends AbstractMultiVisitor {
	
	private final String basePath;
	private final int range;
	private final String separator;

	public CorrelationFitTrackerVisitor(final String basePath, final int range, final String separator ) {
		this( new ArrayList<Visitor>(), basePath, range, separator );
	}

	public CorrelationFitTrackerVisitor( final ArrayList< Visitor > visitors, final String basePath, final int range, final String separator ) {
		super( visitors );
		this.basePath = basePath;
		this.range = range;
		this.separator = separator;
	}

	/* (non-Javadoc)
	 * @see org.janelia.thickness.inference.visitor.AbstractMultiVisitor#actSelf(int, net.imglib2.img.array.ArrayImg, double[], org.janelia.thickness.LUTRealTransform, net.imglib2.img.array.ArrayImg, net.imglib2.img.array.ArrayImg, org.janelia.thickness.FitWithGradient)
	 */
	@Override
	< T extends RealType< T > > void actSelf( 
			final int iteration, 
			final RandomAccessibleInterval< T > matrix, 
			final double[] lut,
			final int[] permutation,
			final int[] inversePermutation,
			final double[] multipliers,
			final double[] weights,
			final RandomAccessibleInterval< double[] > estimatedFits
			) {
		
		if ( estimatedFits == null )
			return;
		
		final File file = new File( String.format( this.basePath, iteration ) );
		try {
			
			file.createNewFile();
			final FileWriter fw = new FileWriter( file.getAbsoluteFile() );
			final BufferedWriter bw = new BufferedWriter( fw );
			
			for ( final double[] estimatedFit : Views.flatIterable( estimatedFits) ) {
				for ( final double v : estimatedFit )
					bw.write( String.format( "%f%s", v, separator ) );
				bw.write(  "\n" );
			}
			
			bw.close();
		} catch (final IOException e) {
			// catch exceptions?
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		

	}

}
