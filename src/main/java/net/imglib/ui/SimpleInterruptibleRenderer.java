package net.imglib.ui;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import net.imglib2.AbstractInterval;
import net.imglib2.RandomAccess;
import net.imglib2.RandomAccessible;
import net.imglib2.RandomAccessibleInterval;
import net.imglib2.converter.Converter;
import viewer.util.StopWatch;

// TODO: shouldn't implement the Interval interface (don't extends AbstractInterval)
public class SimpleInterruptibleRenderer< A, B > extends AbstractInterval
{
	final protected RandomAccessible< A > source;

	final protected Converter< A, B > converter;

	protected long lastFrameRenderNanoTime;

	public SimpleInterruptibleRenderer( final RandomAccessible< A > source, final Converter< A, B > converter )
	{
		super( new long[ source.numDimensions() ] );
		this.source = source;
		this.converter = converter;
		lastFrameRenderNanoTime = -1;
	}

	public boolean map( final RandomAccessibleInterval< B > target, final int numThreads )
	{
		interrupted.set( false );

		final StopWatch stopWatch = new StopWatch();
		stopWatch.start();
		final long startTimeTotal = stopWatch.nanoTime();

		min[ 0 ] = target.min( 0 );
		min[ 1 ] = target.min( 1 );
		max[ 0 ] = target.max( 0 );
		max[ 1 ] = target.max( 1 );

		final long cr = -target.dimension( 0 );

		final int width = ( int ) target.dimension( 0 );
		final int height = ( int ) target.dimension( 1 );

		final ExecutorService ex = Executors.newFixedThreadPool( numThreads );
		final int numTasks;
		if ( numThreads > 1 )
		{
			numTasks = Math.max( numThreads * 10, height );
		}
		else
			numTasks = 1;
		final double taskHeight = ( double ) height / numTasks;
		for ( int taskNum = 0; taskNum < numTasks; ++taskNum )
		{
			final long myMinY = min[ 1 ] + ( int ) ( taskNum * taskHeight );
			final long myHeight = ( (taskNum == numTasks - 1 ) ? height : ( int ) ( ( taskNum + 1 ) * taskHeight ) ) - myMinY - min[ 1 ];

			final Runnable r = new Runnable()
			{
				@Override
				public void run()
				{
					if ( interrupted.get() )
						return;

					final RandomAccess< A > sourceRandomAccess = source.randomAccess( SimpleInterruptibleRenderer.this );
					final RandomAccess< B > targetRandomAccess = target.randomAccess( target );

					sourceRandomAccess.setPosition( min );
					sourceRandomAccess.setPosition( myMinY, 1 );
					targetRandomAccess.setPosition( min[ 0 ], 0 );
					targetRandomAccess.setPosition( myMinY, 1 );
					for ( int y = 0; y < myHeight; ++y )
					{
						if ( interrupted.get() )
							break;
						for ( int x = 0; x < width; ++x )
						{
							converter.convert( sourceRandomAccess.get(), targetRandomAccess.get() );
							sourceRandomAccess.fwd( 0 );
							targetRandomAccess.fwd( 0 );
						}
						sourceRandomAccess.move( cr, 0 );
						targetRandomAccess.move( cr, 0 );
						sourceRandomAccess.fwd( 1 );
						targetRandomAccess.fwd( 1 );
					}
				}
			};
			ex.execute( r );
		}
		ex.shutdown();
		try
		{
			ex.awaitTermination( 1000, TimeUnit.DAYS );
		}
		catch ( final InterruptedException e )
		{
			e.printStackTrace();
		}

		final long lastFrameRenderNanoTime = stopWatch.nanoTime() - startTimeTotal;

		return ! interrupted.get();
	}

	protected AtomicBoolean interrupted = new AtomicBoolean();

	public void cancel()
	{
//		System.out.println( "interrupting..." );
		interrupted.set( true );
	}

	public long getLastFrameRenderNanoTime()
	{
		return lastFrameRenderNanoTime;
	}
}
