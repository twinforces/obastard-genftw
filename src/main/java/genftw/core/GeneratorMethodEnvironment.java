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

import freemarker.cache.ClassTemplateLoader;
import freemarker.cache.FileTemplateLoader;
import freemarker.cache.MultiTemplateLoader;
import freemarker.cache.TemplateLoader;
import freemarker.ext.beans.BeansWrapper;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModelException;
import genftw.core.util.ElementGoodies;

import javax.annotation.processing.Filer;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Runtime environment for processing generator methods.
 */
public class GeneratorMethodEnvironment
{

	private final Configuration templateConfig;
	private final Filer filer;
	private final ProcessorLogger logger;
	private final Elements elementUtils;
	private final ElementGoodies elementGoodies;
	private final String templateRootPath;

	public GeneratorMethodEnvironment(Configuration templateConfig,
	                                  Filer filer, Elements elementUtils, ProcessorLogger logger, String templateRootPath)
	{
		this.templateConfig = templateConfig;
		this.filer = filer;
		this.logger = logger;
		this.elementUtils = elementUtils;
		this.elementGoodies = new ElementGoodies( elementUtils );
		this.templateRootPath = templateRootPath;
	}

	public void process(GeneratorMethod method) throws IOException, TemplateException
	{
		Element methodElement = method.getElement();
		logger.info( "Processing generator method " + methodElement.getSimpleName(), methodElement );

		if ( !method.getOutputRootLocation().isOutputLocation() )
		{
			logger.error( "Output file root location is not an output location", methodElement );
			return;
		}

		// Load template
		Template template = null;
		try
		{
			template = templateConfig.getTemplate( method.getTemplateFile() );
		}
		catch ( IOException e )
		{
			logger.error( "Couldn't find " + method.getTemplateFile() + " using loader " + templateConfig.getTemplateLoader(), e, methodElement );
			throw e;
		}

		// Create template root data-model
		Map<String, Object> rootMap = createTemplateRootModel();


		// Process generator method
		method.process( new GeneratorMethodTemplate( filer, template, rootMap, logger ) );
	}

	Map<String, Object> createTemplateRootModel() throws TemplateModelException
	{
		Map<String, Object> rootMap = new HashMap<String, Object>();

		// Expose Elements instance reference
		rootMap.put( "elementUtils", elementUtils );

		// Expose ElementGoodies instance reference
		rootMap.put( "elementGoodies", elementGoodies );

		// Expose ElementFilter static reference
		rootMap.put( "ElementFilter", BeansWrapper.getDefaultInstance()
				                              .getStaticModels().get( "javax.lang.model.util.ElementFilter" ) );

		// Expose Types static reference
		rootMap.put( "Types", BeansWrapper.getDefaultInstance().getStaticModels().get( "javax.lang.model.util.Types" ) );

		// Expose all available enum classes
		rootMap.put( "enums", BeansWrapper.getDefaultInstance().getEnumModels() );

		return rootMap;
	}

	public void addClassPaths(Set<TypeElement> generators)
	{
// Configure template root directory
		ArrayList<TemplateLoader> loaders = new ArrayList<TemplateLoader>(1+generators.size());

		try {
			FileTemplateLoader ftl1=new FileTemplateLoader(new File(templateRootPath));
			loaders.add(ftl1);
		} catch (IOException e) {
			throw new IllegalArgumentException(logger.formatErrorMessage(
					                                                            "Error while setting template root directory", e), e);
		}
		for (TypeElement ele: generators)
		{
			ClassTemplateLoader ctl = new ClassTemplateLoader( ele.getClass(), "" );
			loaders.add(ctl);
			ClassTemplateLoader ctl2 = new ClassTemplateLoader( ele.getClass(), "templates" );
			loaders.add(ctl2);

		}

		MultiTemplateLoader mtl = new MultiTemplateLoader( loaders.toArray(new TemplateLoader[loaders.size()] ) );
		templateConfig.setTemplateLoader( mtl );
	}
}
