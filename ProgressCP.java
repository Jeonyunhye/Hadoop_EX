import java.net.URI;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileInputStream;
import java.io.BufferedInputStream;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.util.Progressable;
import org.apache.hadoop.conf.Configuration;

public class ProgressCP
{
	public static void main(String[] args) throws Exception
	{
		String localSrc=args[0];
		String dest=args[1];

		InputStream in =new BufferedInputStream(new FileInputStream(localSrc));

		Configuration conf=new Configuration();
		FileSystem fs=FileSystem.get(URI.create(dest), conf);
		OutputStream out=fs.create(new Path(dest), new Progressable()
				{
					public void progress()
					{
						System.out.print(".");
					}
				});
		IOUtils.copyBytes(in, out, 4096, true);
	}
}
