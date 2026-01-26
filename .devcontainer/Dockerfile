FROM mcr.microsoft.com/devcontainers/base:debian

ENV TZ="Europe/Helsinki"

RUN apt-get update && apt-get install -y --no-install-recommends \
    graphviz \
    && apt-get clean

# Create workspace and config directories and set permissions
RUN mkdir -p /workspace /home/vscode/.claude /home/vscode/.m2 && \
    chown -R vscode:vscode /workspace /home/vscode/.claude /home/vscode/.m2

WORKDIR /workspace

# Set up non-root user
USER vscode

# Install Claude
ENV PATH=$PATH:/home/vscode/.local/bin
RUN curl -fsSL https://claude.ai/install.sh | bash
