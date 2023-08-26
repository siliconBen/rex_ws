/*
This is an implementation of Bayesian linear regression with Thompson sampling.
*/

import org.apache.commons.math3.distribution.*;
import Jama.Matrix;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.net.*;
import com.google.gson.*;

public class Learning
{
	//the reward value to use if the system returns no metrics at all
	private static double NO_METRICS_REWARD = 10000.00;
	
	final String HOST = "127.0.0.1";
	final int PORT = 8008;
	final int RUNS = 100;
	final int ITERATIONS = 100;
	
	private Metric getMetric(PerceptionData pd, String name)
	{
	for (int i = 0; i < pd.metrics.length; i++)
		{
		if (pd.metrics[i].name.equals(name))
			return pd.metrics[i];
		}
	
	return null;
	}
	
	private Learning() throws Exception
	{
		
		//acquire list of available configurations from PAL
		Socket socket = new Socket(HOST, PORT);
		String request = "GET /meta/get_all_configs HTTP/1.0\r\n\r\n";
		OutputStream os = socket.getOutputStream();
		os.write(request.getBytes());
		os.flush();
		
		InputStream is = socket.getInputStream();
		int ch;
		StringBuilder rb = new StringBuilder();
		while( (ch=is.read())!= -1)
			rb.append((char)ch);
		String response = rb.toString();
		socket.close();

		String[] responseParts = response.split("\r\n\r\n");
		
		// - parse the response into JSON and extract the array of configurations
		Gson gson = new Gson();
		Configs cfgs = gson.fromJson(responseParts[1], Configs.class);
		
		String configurations[] = new String[cfgs.configs.length];
		
		for (int i = 0; i < cfgs.configs.length; i++)
		{
			configurations[i] = cfgs.configs[i];
			//System.out.println(" -- " + configurations[i]);
		}
		
		//Set up action matrix with all possible/valid configs
		Matrix actions = getActionMatrix(configurations.clone());
		
		//Initialise prior mus with base param
		Matrix priorMus = new Matrix(actions.getColumnDimension(), 1);
		priorMus.set(0, 0, 1);

		double c = 0.1;
		//Initial lambda = c * I
		Matrix priorLambda = Matrix.identity(actions.getColumnDimension(), actions.getColumnDimension()).times(c); 
		
		double priorA = 1;
		double priorB = 0.1;
		
		double[] results = new double[ITERATIONS];
		BufferedWriter writer = new BufferedWriter(new FileWriter("results.txt"));
		for (int run = 0; run < RUNS; run++)
		{
			try
			{
				//Initial posteriors = priors
				Matrix posteriorMus = new Matrix(priorMus.getArray());
				Matrix posteriorLambda = new Matrix(priorLambda.getArray());
				double posteriorA = priorA;
				double posteriorB = priorB;

				int[] selectedActionsCount = new int[actions.getRowDimension()];
				
				
				Matrix xtByX = new Matrix(actions.getColumnDimension(), actions.getColumnDimension());	
				Matrix xtByY = new Matrix(actions.getColumnDimension(), 1);	
				Matrix ytByY = new Matrix(1, 1);
				MultivariateNormalDistribution dist = null;
				
				int lastPicked = -1;
				for (int n = 0; n < ITERATIONS; n++)
				{	
					double variance = posteriorB / (new GammaDistribution(posteriorA, 1).sample());
					int maxValueLocation = -1;
					
					try
					{
						//Draw sample based on priors as a prediction
						dist = new MultivariateNormalDistribution(columnVectorToArray(posteriorMus.getArray()),
																	posteriorLambda.inverse().times(variance).getArray());
						
						Matrix sample = new Matrix(arrayToColumnVector(dist.sample()));	
						Matrix actionsBySample = actions.times(sample);
						
						double maxValue = -Double.MAX_VALUE;
						for (int i = 0; i < actionsBySample.getRowDimension(); i++)
						{
							if (actionsBySample.getArray()[i][0] > maxValue)
							{
								//Find best value out of sample to actually test
								maxValue = actionsBySample.getArray()[i][0];
								maxValueLocation = i;
							}
						}
					}
					catch(Exception e)
					{
						//NOTE: somtimes we get an exception from the math libraries above; in this case we just continue as we were...
						e.printStackTrace();
						maxValueLocation = lastPicked;
					}
					
					System.out.println("Iteration number " + n);
					System.out.println("Picked: " + (maxValueLocation + 1));
					
					selectedActionsCount[maxValueLocation]++;
					xtByX = getXtByX(actions, selectedActionsCount);
					
					// -- adapt to the selected configuration, if different from the last one --
					
					if (lastPicked != maxValueLocation)
					{
						lastPicked = maxValueLocation;
						String config = "{\"config\" : \"" + configurations[maxValueLocation] + "\"}";
						
						socket = new Socket(HOST, PORT);
						request = "POST /meta/set_config" + " HTTP/1.0\r\n"
									+ "Content-Type: text/json\r\n"
									+ "Content-Length: " + config.length() + "\r\n"
									+ "\r\n"
									+ config;
						os = socket.getOutputStream();
						os.write(request.getBytes());
						os.flush();
						
						is = socket.getInputStream();
						while( (ch=is.read())!= -1)
							System.out.print((char)ch);
						socket.close();
						System.out.println("");
					}
					
					// -- wait for the length of the observation window, then collect metrics from this configuration --
					
					double reward = -1;
					while (reward < 0)
					{
						System.out.println("[sleeping]\n");
						Thread.sleep(10000);
						socket = new Socket(HOST, PORT);
						request = "GET /meta/get_perception HTTP/1.0\r\n\r\n";
						os = socket.getOutputStream();
						os.write(request.getBytes());
						os.flush();
						
						is = socket.getInputStream();
						rb = new StringBuilder();
						while( (ch=is.read())!= -1)
							rb.append((char)ch);
						response = rb.toString();
						socket.close();  
						
						responseParts = response.split("\r\n\r\n");
						
						// - parse the response into JSON and extract the value for "response_time"
						gson = new Gson();
						PerceptionData pd = gson.fromJson(responseParts[1], PerceptionData.class);
						
						if (pd != null && pd.metrics != null && getMetric(pd, "response_time") != null)
							{
							Metric m = getMetric(pd, "response_time");
							
							reward = m.value / m.count;
							reward = 1.0 / reward;
							results[n] = reward;
							System.out.println("observed reward: " + reward);
							}
							else
							{
							System.out.println("[no observed reward]");
							
							//we assume this is a bad configuration...
							// (**this is only the right thing to do if the server actually received any requests - check events array? **)
							reward = 1.0 / NO_METRICS_REWARD;
							results[n] = reward;
							}
					}
					
					
					xtByY = getXtByY(actions, reward, maxValueLocation, xtByY);	
					ytByY.set(0, 0, Math.pow(reward, 2) + ytByY.get(0, 0));
					
					//Calculate posteriors
					posteriorLambda = xtByX.plus(priorLambda);
					posteriorMus = posteriorLambda.inverse().times(priorLambda.times(priorMus).plus(xtByY));
					
					posteriorA = priorA + ((double)(n - 1) / 2);
					posteriorB = priorB + (ytByY.plus(priorMus.transpose().times(priorLambda).times(priorMus)).minus(posteriorMus.transpose().times(posteriorLambda).times(posteriorMus)).times(0.5)).getArray()[0][0];
				
				}
				
				for (int i = 0; i < ITERATIONS; i++)
				{
					String output =  Double.toString(results[i]) + '\t';
					writer.write(output, 0, output.length());
					writer.flush();
				}
				writer.write("\n", 0, "\n".length());
				writer.flush();
			}
			catch (Exception e)
			{
				run--;
				e.printStackTrace();
				continue;
			}
		}
		// printDouble2DArray(posteriorMus.getArray());
		// printDouble2DArray(priorLambda.inverse().times(variance).getArray());
	}
	
	private Matrix getActionMatrix(String[] configurations)
	{	
		ArrayList<String[]> configComponents = new ArrayList<String[]>();
		ArrayList<String> uniqueComponents = new ArrayList<String>();
		
		for (int i = 0; i < configurations.length; i++)
		{
			//Remove connection info, not necessary
			int end = configurations[i].lastIndexOf("|");
			if (end >= 0)
			{
				configurations[i] = configurations[i].substring(0, end);
			}
			
			String[] components = configurations[i].split(",");
			for (String s : components)
			{
				if (!uniqueComponents.contains(s))
				{
					uniqueComponents.add(s);
				}
			}
			
			configComponents.add(components);
		}
		
		//Create matrix of proper dimensions
		Matrix actions = new Matrix(configurations.length, uniqueComponents.size() + 1);
		//Set first column to all 1's
		for (int m = 0; m < configurations.length; m++)
		{
			actions.set(m, 0, 1);
		}
		
		//Set rows to correct configurations based on components present
		for (int m = 0; m < actions.getRowDimension(); m++)
		{
			for (int n = 0; n < uniqueComponents.size(); n++)
			{
				for (int i = 0; i < configComponents.get(m).length; i++)
				{
					if (uniqueComponents.get(n).equals(configComponents.get(m)[i]))
					{
						actions.set(m, n + 1, 1);
						break;
					}
				}
			}
		}
		
		//Check each column to see if it's a repeat of another
		HashSet<Integer> repeatColumns = new HashSet<Integer>();
		for (int n = 0; n < actions.getColumnDimension(); n++)
		{
			while (repeatColumns.contains(n))
			{
					n++;
					//Break if we're going past the matrix dimensions
					if (n >= actions.getColumnDimension() - 1)
					{
						break;
					}
			}
			
			for (int columnToCheck = n + 1; columnToCheck < actions.getColumnDimension(); columnToCheck++)
			{
				while (repeatColumns.contains(columnToCheck))
				{
					columnToCheck++;
					//Break if we're going past the matrix dimensions
					if (columnToCheck >= actions.getColumnDimension() - 1)
					{
						break;
					}
				}
				
				if (columnToCheck >= actions.getColumnDimension())
				{
					break;
				}
				
				boolean isRepeat = true;
				for (int m = 0; m < actions.getRowDimension(); m++)
				{
					if (actions.get(m, n) != actions.get(m, columnToCheck))
					{
						isRepeat = false;
						break;
					}
				}
				if (isRepeat)
				{
					repeatColumns.add(columnToCheck);
				}
			}
		}
		
		//Remove repeated columns
		if (repeatColumns.size() > 0)
		{
			Matrix actionsRepeatsRemoved = new Matrix(actions.getRowDimension(), actions.getColumnDimension() - repeatColumns.size());
			int i = 0;
			for (int n = 0; n < actions.getColumnDimension(); n++)
			{
				while (repeatColumns.contains(n))
				{
					n++;
				}
				if (n >= actions.getColumnDimension())
				{
					break;
				}
				for (int m = 0; m < actions.getRowDimension(); m++)
				{
					actionsRepeatsRemoved.set(m, i, actions.get(m, n));
				}
				i++;
			}
			actions = actionsRepeatsRemoved;
		}
		

		printDouble2DArray(actions.getArray());
		return actions;
	}
	
	//XT by X for selected actions
	private Matrix getXtByX(Matrix actions, int[] selectedActionsCount)
	{
		//Calculate Xt by X without storing a matrix of all actions taken
		Matrix xtByX = new Matrix(actions.getColumnDimension(), actions.getColumnDimension());		
		for (int i = 0; i < selectedActionsCount.length; i++)
		{
			//If an action has been selected at least once, calculate that action transpose by action
			if (selectedActionsCount[i] > 0)
			{
				Matrix action = new Matrix(1, actions.getColumnDimension());
				for (int x = 0; x < actions.getColumnDimension(); x++)
				{
					action.set(0, x, actions.get(i, x));
				}
				//Multiply transpose by action
				action = action.transpose().times(action);
				//Multiply by number of times action is taken
				action = action.times(selectedActionsCount[i]);
				xtByX = xtByX.plus(action);
			}
		}
		return xtByX;
	}
	
	private Matrix getXtByY(Matrix actions, double reward, int maxValueLocation, Matrix xtByY)
	{
		Matrix action = new Matrix(1, actions.getColumnDimension());
		for (int x = 0; x < actions.getColumnDimension(); x++)
		{
			action.set(0, x, actions.get(maxValueLocation, x));
		}
		return xtByY.plus(action.times(reward).transpose());
	}
	
	//Take an x long array and make it 1 by x 
	private double[][] arrayToColumnVector(double[] d)
	{
		double[][] d2 = new double[d.length][1];
		for (int i = 0; i < d.length; i++)
		{
			d2[i][0] = d[i];
		}
		return d2;
	}
	
	//Take a 1 by x array and make it an x long array
	private double[] columnVectorToArray(double[][] d2)
	{
		if (d2[0].length > 1)
		{
			System.out.println("Not a column vector.");
			return null;
		}
		double[] d = new double[d2.length];
		for (int i = 0; i < d2.length; i++)
		{
			d[i] = d2[i][0];
		}
		return d;
	}
	
	//Print a 2d array to console
	private void printDouble2DArray(double[][] d)
	{
		final int DECIMAL_PLACES = 1;
		for (int x = 0; x < d.length; x++)
		{
			for (int y = 0; y < d[x].length; y++)
			{
				System.out.print((Math.round(Math.pow(10, DECIMAL_PLACES) * d[x][y]) / Math.pow(10, DECIMAL_PLACES))  + " ");
			}
			System.out.println();
		}
	}
	
	public static void main(String args[]) throws Exception
	{
		new Learning();
	}
}
