oc login -u system:admin
oc create -f ../os-templates/hostPath/pvconf.yaml
oc create -f ../os-templates/hostPath/pvdata.yaml
oc create -f ../os-templates/hostPath/pvworkspace.yaml
