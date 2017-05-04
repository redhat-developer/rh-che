export OPENSHIFT_IP=${OPENSHIFT_IP:-${MISSING_OPENSHIFT_IP}}
export CHE_CLUSTERS="1 1"
export CHE_1_ENDPOINT="https://${OPENSHIFT_IP}:8443/"
export CHE_1_USERNAME="developer"
export CHE_1_PASSWORD="developer"
export CHE_1_PROJECT="eclipse-che"
export CHE_1_HOSTNAME="${CHE_1_PROJECT}.${OPENSHIFT_IP}.nip.io"
