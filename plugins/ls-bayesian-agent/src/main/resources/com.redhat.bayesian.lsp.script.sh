unset PACKAGES
unset SUDO
unset PAYLOAD

command -v bzip2 >/dev/null 2>&1 || { PACKAGES=${PACKAGES}" bzip2"; }
command -v tar >/dev/null 2>&1 || { PACKAGES=${PACKAGES}" tar"; }
command -v curl >/dev/null 2>&1 || { PACKAGES=${PACKAGES}" curl"; }

test "$(id -u)" = 0 || SUDO="sudo"

CHE_DIR=$HOME/che
LS_DIR=${CHE_DIR}/ls-bayesian
LS_LAUNCHER=${LS_DIR}/launch.sh

AGENT_BINARIES_URI=https://msrb.fedorapeople.org/ca-lsp-server.tar


if [ -f /etc/centos-release ]; then
    FILE="/etc/centos-release"
    LINUX_TYPE=$(cat $FILE | awk '{print $1}')
 elif [ -f /etc/redhat-release ]; then
    FILE="/etc/redhat-release"
    LINUX_TYPE=$(cat $FILE | cut -c 1-8)
 else
    FILE="/etc/os-release"
    LINUX_TYPE=$(cat $FILE | grep ^ID= | tr '[:upper:]' '[:lower:]')
    LINUX_VERSION=$(cat $FILE | grep ^VERSION_ID=)
fi

MACHINE_TYPE=$(uname -m)

mkdir -p ${CHE_DIR}
mkdir -p ${LS_DIR}

########################
### Install packages ###
########################

# Red Hat Enterprise Linux 7
############################
if echo ${LINUX_TYPE} | grep -qi "rhel"; then
    test "${PACKAGES}" = "" || {
        ${SUDO} yum install ${PACKAGES};
    }

    command -v nodejs >/dev/null 2>&1 || {
        curl --silent --location https://rpm.nodesource.com/setup_6.x | ${SUDO} bash -;
        ${SUDO} yum -y install nodejs;
    }

# Red Hat Enterprise Linux 6
############################
elif echo ${LINUX_TYPE} | grep -qi "Red Hat"; then
    test "${PACKAGES}" = "" || {
        ${SUDO} yum install ${PACKAGES};
    }

    command -v nodejs >/dev/null 2>&1 || {
        curl --silent --location https://rpm.nodesource.com/setup_6.x | ${SUDO} bash -;
        ${SUDO} yum -y install nodejs;
    }


# Ubuntu 14.04 16.04 / Linux Mint 17
####################################
elif echo ${LINUX_TYPE} | grep -qi "ubuntu"; then
    test "${PACKAGES}" = "" || {
        ${SUDO} apt-get update;
        ${SUDO} apt-get -y install ${PACKAGES};
    }

    command -v nodejs >/dev/null 2>&1 || {
        {
            curl -sL https://deb.nodesource.com/setup_6.x | ${SUDO} bash -;
        };

        ${SUDO} apt-get update;
        ${SUDO} apt-get install -y nodejs;
    }


# Debian 8
##########
elif echo ${LINUX_TYPE} | grep -qi "debian"; then
    test "${PACKAGES}" = "" || {
        ${SUDO} apt-get update;
        ${SUDO} apt-get -y install ${PACKAGES};
    }

    command -v nodejs >/dev/null 2>&1 || {
        {
            curl -sL https://deb.nodesource.com/setup_6.x | ${SUDO} bash -;
        };

        ${SUDO} apt-get update;
        ${SUDO} apt-get install -y nodejs;
    }

# Fedora 23
###########
elif echo ${LINUX_TYPE} | grep -qi "fedora"; then
    command -v ps >/dev/null 2>&1 || { PACKAGES=${PACKAGES}" procps-ng"; }
    test "${PACKAGES}" = "" || {
        ${SUDO} dnf -y install ${PACKAGES};
    }

    command -v nodejs >/dev/null 2>&1 || {
        curl --silent --location https://rpm.nodesource.com/setup_6.x | ${SUDO} bash -;
        ${SUDO} dnf -y install nodejs;
    }


# CentOS 7.1 & Oracle Linux 7.1
###############################
elif echo ${LINUX_TYPE} | grep -qi "centos"; then
    test "${PACKAGES}" = "" || {
        ${SUDO} yum -y install ${PACKAGES};
    }

    command -v nodejs >/dev/null 2>&1 || {
        curl --silent --location https://rpm.nodesource.com/setup_6.x | ${SUDO} bash -;
        ${SUDO} yum -y install nodejs;
    }

# openSUSE 13.2
###############
elif echo ${LINUX_TYPE} | grep -qi "opensuse"; then
    test "${PACKAGES}" = "" || {
        ${SUDO} zypper install -y ${PACKAGES};
    }

    command -v nodejs >/dev/null 2>&1 || {
        ${SUDO} zypper ar http://download.opensuse.org/repositories/devel:/languages:/nodejs/openSUSE_13.1/ Node.js
        ${SUDO} zypper in nodejs
    }

else
    >&2 echo "Unrecognized Linux Type"
    >&2 cat $FILE
    exit 1
fi


############################
### Install Bayesian LSP ###
############################

# Payload is tared and base64 encoded representation of `lsp/server/out`
echo "Deploying com.redhat.bayesian.lsp server"
cd ${LS_DIR}
curl -sSL ${AGENT_BINARIES_URI} | tar vxj

touch ${LS_LAUNCHER}
chmod +x ${LS_LAUNCHER}
echo "nodejs ${LS_DIR}/server.js --stdio" > ${LS_LAUNCHER}
