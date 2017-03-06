oc login -u developer -p developer
oc new-project eclipse-che
# Create a serviceaccount with privileged scc
oc login -u system:admin -n eclipse-che
oc create serviceaccount cheserviceaccount
oc adm policy add-scc-to-user privileged -z cheserviceaccount
oc adm policy add-cluster-role-to-user cluster-admin system:serviceaccount:eclipse-che:cheserviceaccount
