/*******************************************************************************
 * Copyright (c) 2011 itemis AG (http://www.itemis.eu) and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *******************************************************************************/
package org.eclipse.xtext.generator;

import java.util.Set;

import org.eclipse.emf.ecore.resource.Resource;

import com.google.inject.ImplementedBy;

/**
 * Interface for providing default output configurations. These do not consider any context such as
 * an IDE project with specific output folder settings. If you need the actual output configurations
 * for a resource in a specific context, use {@link IContextualOutputConfigurationProvider} instead.
 * 
 * @author Michael Clay - Initial contribution and API
 * @since 2.1
 */
@ImplementedBy(OutputConfigurationProvider.class)
public interface IOutputConfigurationProvider {
	
	/**
	 * Return the default output configurations without considering any context.
	 */
	Set<OutputConfiguration> getOutputConfigurations();

	class Delegate implements IOutputConfigurationProvider, IContextualOutputConfigurationProvider {
		private IOutputConfigurationProvider delegate;

		public IOutputConfigurationProvider getDelegate() {
			return delegate;
		}

		public Delegate(IOutputConfigurationProvider delegate) {
			super();
			this.delegate = delegate;
		}

		@Override
		public Set<OutputConfiguration> getOutputConfigurations() {
			return delegate.getOutputConfigurations();
		}

		/**
		 * @since 2.8
		 */
		@Override
		public Set<OutputConfiguration> getOutputConfigurations(Resource context) {
			if (delegate instanceof IContextualOutputConfigurationProvider) {
				return ((IContextualOutputConfigurationProvider) delegate).getOutputConfigurations(context);
			}
			return delegate.getOutputConfigurations();
		}

	}
}
