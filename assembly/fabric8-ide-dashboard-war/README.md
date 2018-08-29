## Eclipse Che dashboard for OpenShift.io

This module builds a modified version of the Eclipse Che dashboard to include changes necessary in the downstream version of rh-che. 

During the maven build, the contents of the `./src` directory are copied over the upstream dashboard source tree. This means that the structure of the `./src` directory in this repo should match that of upstream. Files with identical names and paths will be copied over upstream sources, while unique files will be added.

The build process is fairly non-standard, as the upstream project is an angularjs project, and not java. The steps are:

1. The upstream dashboard's source files are unpacked to the directory `./target/sources` via the maven dependency plugin. A diff between this unpacked directory and the local `./src` directory is taken and saved to `./target/source_tree.diff`.
2. The contents and structure of `./src` are copied *over* `./target/sources`
3. The dashboard is built in a docker container, as in the upstream build, using the Dockerfile at this root of this submodules source tree.
4. The docker image is used to extract the built dashboard to `./target/dist`
5. The base path for the deployed dashboard is changed `/dashboard` 
6. The maven war plugin is used to build the final `war` file using resources in `./target/dist`

Steps 3-6 are basically the upstream build, except modified slightly to update paths (e.g. sources being `./target/sources` instead of `./src`)

