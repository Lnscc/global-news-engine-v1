# Repository instructions

## API and Postman

Whenever a REST API endpoint or its request/response contract is added, changed, or removed:

- Update the corresponding Postman collection under `docs/postman` in the same change.
- Add or update Postman tests for the affected status codes and response contract.
- Validate that the resulting Postman collection is valid JSON.

## Local tickets

When implementing a ticket under `docs/tickets`:

- Add an implementation comment to the ticket in the same change.
- Summarize the implemented behavior.
- Do not change the ticket status or move the ticket unless explicitly requested. But ask for a status change.
