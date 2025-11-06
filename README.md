# Apify Camunda Connector

This connector is used to interact with the Apify API in the **Camunda 8** environment.

## Development setup guide

Follow these steps to run and test your custom outbound connector with your locally hosted Camunda stack in Docker.

### Prerequisites:

#### 1. Camunda stack in Docker

Locally spin up a Camunda stack using Docker Compose following [this quickstart guide](https://docs.camunda.io/docs/self-managed/quickstart/developer-quickstart/docker-compose).

📌 **Note: Make sure to install FULLY configured Camunda stack which includes the Modeler.**

In case you want to choose a specific version, you can find their `docker-compose.yaml` files in [Camunda's official repository](https://github.com/camunda/camunda-distributions/tree/main/docker-compose/versions).

#### 2. Apify Camunda Connector

Clone this repository and build the connector:

```bash
git clone https://github.com/apify/apify-camunda-connector.git

cd apify-camunda-connector

mvn clean package
```

### Run your connector locally

**Important:** Before proceeding with Camunda Modeler, make sure both the Camunda stack and your connector are running locally.

1. Ensure your Camunda stack is running in Docker. If you haven't started it yet, spin it up using Docker Compose (see Prerequisites above).

2. Start the local runtime to expose your connector:

```bash
mvn exec:java -Dexec.mainClass="io.camunda.example.LocalConnectorRuntime"
```

Keep this terminal running while you work with Camunda Modeler.

### Set up Camunda Modeler and test the connector

1. Go to Camunda Modeler and create a new project.

![Creating a new project](docs/modeler-create-project.png)

2. Upload your connector template JSON from this repository.

- Outbound connector template: `element-templates/apify-outbound-connector.json`
- Inbound connector template: `element-templates/apify-inbound-connector.json`

![Uploading the connector template JSON](docs/modeler-upload-template.png)

3. Publish the connector template to the project.

![Publishing the connector template](docs/modeler-publish-template.png)

4. Create a new BPMN diagram.

![Creating a new BPMN diagram](docs/modeler-create-bpmp-diagram.png)

5. Design a process using the Apify BPMN connector as a BPMN service task.

![Designing a process using the Apify BPMN connector as a BPMN service task](docs/modeler-create-apify-bpmn-task.png)

6. Set the connector input variables and run the process.

![Setting the connector input variables and running the process](docs/modeler-set-inputs-and-run.png)

7. Verify the run status and result in Camunda Operate.

![Verifying the run status and result in Camunda Operate](docs/operate-check-run-result.png)
