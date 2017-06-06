package com.redhat.che.nosvn.ide;

import org.eclipse.che.api.promises.client.Promise;
import org.eclipse.che.api.promises.client.PromiseProvider;
import org.eclipse.che.ide.api.subversion.Credentials;
import org.eclipse.che.ide.api.subversion.SubversionCredentialsDialog;

import com.google.inject.Inject;

public class NoSvnSubversionCredentialsDialog implements SubversionCredentialsDialog {

    PromiseProvider promiseProvider;
    
    @Inject
    public NoSvnSubversionCredentialsDialog(PromiseProvider promiseProvider) {
        this.promiseProvider = promiseProvider;
    }
    
    @Override
    public Promise<Credentials> askCredentials() {
        return promiseProvider.reject("Subversion is not supported");
    }
}
