## WebApp Configuration
### Adding servers
In `config.json`, add and remove the `servers` attribute.
E.g., to control `10.0.2.1` and `10.0.2.2`, configure

    "servers": ["10.0.2.1", "10.0.2.2"]

### Adding projects
In `config.json`, add the name of the main method for status detection.
E.g., to add a main method `AntimalwareMain`, configure

    "mainNames": ["Clasp", "AntimalwareMain"]
