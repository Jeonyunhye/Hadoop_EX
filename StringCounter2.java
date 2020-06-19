import java.util.Set;
import java.util.List;
import java.net.URI;
import java.util.HashSet;
import java.io.FileReader;
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.IOException;
import java.util.StringTokenizer;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.IntWritable;
import org.apache.hadoop.util.StringUtils;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.Reducer;
import org.apache.hadoop.mapreduce.Counter;
import org.apache.hadoop.util.GenericOptionsParser;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;


public class StringCounter2
{
        public static class TokenMapper extends Mapper<Object, Text, Text, IntWritable>
	{
		static enum CountersEnum {INPUT_WORDS}

		private final static IntWritable one=new IntWritable(1);
		private Text word = new Text();

		private boolean caseSensitive;
		private Set<String> patternsToSkip = new HashSet<String>();

		private Configuration conf;
		private BufferedReader fis;

		@Override
		public void setup(Context context) throws IOException, InterruptedException
		{
			conf=context.getConfiguration();
			caseSensitive=conf.getBoolean("StringCounter.case.sensitive", true);
			if(conf.getBoolean("StringCounter.skip.patterns", false))
			{
				URI[] patternsURIs=Job.getInstance(conf).getCacheFiles();
				for(URI patternsURI : patternsURIs)
				{
					Path patternsPath = new Path(patternsURI.getPath());
					String patternsFileName=patternsPath.getName().toString();
					parseSkipFile(patternsFileName);
				}
			}
		}
		
		private void parseSkipFile(String fileName)
		{
			try{
				fis=new BufferedReader(new FileReader(fileName));
				String pattern=null;
				while((pattern = fis.readLine())!=null)
				{
					patternsToSkip.add(pattern);
				}
			}
			catch(IOException ioe)
			{	
				System.err.println("Caught exception while parsing the cached file '"+StringUtils.stringifyException(ioe));
			}
		}

		@Override
		public void map(Object key, Text value, Context context) throws IOException, InterruptedException
		{
			String line=(caseSensitive)?value.toString(): value.toString().toLowerCase();
			for(String pattern : patternsToSkip)
			{
				line=line.replaceAll(pattern, "");
			}
			StringTokenizer itr =new StringTokenizer(line);
			while(itr.hasMoreTokens())
			{
				word.set(itr.nextToken());
				context.write(word, one);
				Counter counter = context.getCounter(CountersEnum.class.getName(), CountersEnum.INPUT_WORDS.toString());
				counter.increment(1);
			}
		}
	}
	
	public static class CountReducer extends Reducer<Text, IntWritable, Text, IntWritable>
	{
		private IntWritable result=new IntWritable();

		public void reduce(Text key, Iterable<IntWritable> values, Context context) throws IOException, InterruptedException
		{
			int sum=0;
			for(IntWritable value : values)
			{
				sum+=value.get();
			}
			result.set(sum);
			context.write(key, result);
		}
	}

	public static void main(String[] args) throws Exception
	{
		Configuration conf = new Configuration();
		GenericOptionsParser optionParser = new GenericOptionsParser(conf, args);
		String[] remainingArgs = optionParser.getRemainingArgs();

		if((remainingArgs.length !=2)&& (remainingArgs.length!=4))
		{
			System.err.println("sage:StringCounter <in> <out> [-skip skipPatternFile]");
			System.exit(2);
		}

		Job job = Job.getInstance(conf, "word count");
		job.setJarByClass(StringCounter2.class);
		job.setMapperClass(TokenMapper.class);
		job.setCombinerClass(CountReducer.class);
		job.setReducerClass(CountReducer.class);
		job.setOutputKeyClass(Text.class);
		job.setOutputValueClass(IntWritable.class);

		List<String> otherArgs=new ArrayList<String>();
		for(int i =0; i<remainingArgs.length; ++i)
		{
			if("-skip".equals(remainingArgs[i]))
			{
				job.addCacheFile(new Path(remainingArgs[++i]).toUri());
				job.getConfiguration().setBoolean("StringCounter.skip.patterns", true);
			}
			else
			{
				otherArgs.add(remainingArgs[i]);
			}
		}

		FileInputFormat.addInputPath(job, new Path(otherArgs.get(0)));
		FileOutputFormat.setOutputPath(job, new Path(otherArgs.get(1)));
		System.exit(job.waitForCompletion(true)?0:1);
	}
}



