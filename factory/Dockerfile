# Dark factory image: JDK 21 + Node 22 + pnpm + gh CLI + pi coding agent.
# No secrets are baked in; everything is injected at container runtime.
# Built and published by .github/workflows/factory-image.yml.

ARG PI_VERSION=1.4.0

FROM eclipse-temurin:21-jdk-jammy

ARG PI_VERSION

ENV DEBIAN_FRONTEND=noninteractive

# Base tooling + gh CLI (official apt repo) + ripgrep (used by pi's tooling).
RUN apt-get update \
  && apt-get install -y --no-install-recommends \
       bash ca-certificates curl git jq ripgrep \
       gpg wget \
  && curl -fsSL https://cli.github.com/packages/githubcli-archive-keyring.gpg \
       | dd of=/usr/share/keyrings/githubcli-archive-keyring.gpg \
  && chmod go+r /usr/share/keyrings/githubcli-archive-keyring.gpg \
  && echo "deb [arch=$(dpkg --print-architecture) signed-by=/usr/share/keyrings/githubcli-archive-keyring.gpg] https://cli.github.com/packages stable main" \
       > /etc/apt/sources.list.d/github-cli.list \
  && apt-get update \
  && apt-get install -y --no-install-recommends gh \
  && rm -rf /var/lib/apt/lists/*

# Node.js 22 LTS (pi requires >= 22.19) via NodeSource.
RUN curl -fsSL https://deb.nodesource.com/setup_22.x | bash - \
  && apt-get install -y --no-install-recommends nodejs \
  && rm -rf /var/lib/apt/lists/* \
  && corepack enable

# pi coding agent, pinned.
RUN npm install -g --ignore-scripts "@earendil-works/pi-coding-agent@${PI_VERSION}" \
  && pi --version

# Non-root user.
RUN useradd --create-home --shell /bin/bash factory

# Factory scripts and skills (skills also go to ~/.agents/skills so pi
# discovers them even if the target repo ships none of its own).
COPY factory.sh /factory/factory.sh
COPY skills /opt/factory/skills
RUN chmod 755 /factory/factory.sh \
  && mkdir -p /home/factory/.agents/skills /workspace /factory-logs \
  && cp -r /opt/factory/skills/* /home/factory/.agents/skills/ \
  && chown -R factory:factory /home/factory /workspace /factory-logs

USER factory
WORKDIR /workspace

ENTRYPOINT ["/factory/factory.sh"]
