/*
 * Copyright 2011 GenFTW contributors
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package genftw.core;

import freemarker.template.Template;
import freemarker.template.TemplateException;

import javax.annotation.processing.Filer;
import javax.annotation.processing.FilerException;
import javax.lang.model.element.Element;
import javax.lang.model.util.Elements;
import javax.tools.FileObject;
import javax.tools.JavaFileManager.Location;
import java.io.*;
import java.util.HashMap;
import java.util.Map;

/**
 * Wraps a FreeMarker {@link Template}, allowing repeated template processing.
 */
public class GeneratorMethodTemplate
{

	private final Filer filer;
	private final Template template;
	private final ProcessorLogger logger;
	private final Map<String, Object> rootMap;

	public GeneratorMethodTemplate(Filer filer, Template template,
	                               Map<String, Object> rootMap, ProcessorLogger logger)
	{
		this.filer = filer;
		this.template = template;
		this.logger = logger;
		// Create defensive copy of template data-model to prevent corrupting the original instance
		this.rootMap = new HashMap<String, Object>( rootMap );
	}

	public void setRootModelMapping(String key, Object value)
	{
		rootMap.put( key, value );
	}

	public String resolveOutputFile(Element elm, String outputFile) throws IOException, TemplateException
	{


		try
		{
			Template outputTemplate = new Template( "outputLocation", new StringReader( outputFile ),
					                                      template.getConfiguration() );

			HashMap<String, Object> outputRootMap = new HashMap<String, Object>( rootMap );    // copy so we can't over write

			if ( elm != null )
			{
				outputRootMap.put( "elementSimpleName", elm.getSimpleName() );
				outputRootMap.put( "packageElementPath", ( (Elements) rootMap.get( "elementUtils" ) ).getPackageOf( elm ).getQualifiedName().toString().replace( ".", "/" ) );
			}

			StringWriter outputResult = new StringWriter();
			outputTemplate.process( outputRootMap, outputResult );
			return outputResult.toString();
		}
		catch ( IOException e )
		{
			logger.error( "I/O Error resolving output file: " + outputFile, e, elm );
			throw e;
		}
		catch ( TemplateException e )
		{
			logger.error( "Template Error resolving output file: " + outputFile, e, elm );
			throw e;
		}

	}

	public void process(Location outputRootLocation, String outputFile) throws IOException, TemplateException
	{
		logger.info( "Generating " + outputFile );

		PrintWriter outputWriter = null;
		try
		{
			// Create output writer
			FileObject resource = filer.createResource( outputRootLocation, "", outputFile );
			outputWriter = new PrintWriter( new BufferedWriter( resource.openWriter() ) );

			// Process template
			template.process( rootMap, outputWriter );
		}
		catch (FilerException ex)
		{
			if (ex.getMessage().startsWith("Attempt to reopen"))
			{
				logger.info("Ignoring regeneration of " + outputFile);
			}
			else
			{
				logger.error( "Filer exception output file: " + outputFile, ex,null );
				throw ex;
			}
		}
		finally
		{
			// Close output writer
			if ( outputWriter != null )
			{
				outputWriter.close();
			}
		}
	}

}
