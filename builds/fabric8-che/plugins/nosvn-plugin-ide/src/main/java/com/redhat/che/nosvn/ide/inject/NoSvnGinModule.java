package com.redhat.che.nosvn.ide.inject;

import org.eclipse.che.ide.api.extension.ExtensionGinModule;
import org.eclipse.che.ide.api.subversion.SubversionCredentialsDialog;
import org.eclipse.che.ide.rest.AsyncRequestFactory;

import com.google.gwt.inject.client.AbstractGinModule;
import com.redhat.che.nosvn.ide.NoSvnSubversionCredentialsDialog;

/**
 * NoSvnGinModule
 */
@ExtensionGinModule
public class NoSvnGinModule extends AbstractGinModule{
    
    @Override
    public void configure(){
        bind(SubversionCredentialsDialog.class).to(NoSvnSubversionCredentialsDialog.class);
    }
}