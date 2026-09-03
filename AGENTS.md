# Repository instructions

## Required skills

- Always use the `keep-it-simple` skill for every task in this repository.

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

## Backlog prioritization

- Treat every ticket under `docs/tickets/backlog` as deliberately deferred, regardless of its `Status:` value.
- Do not present backlog tickets as actionable next work or recommend them unless the user explicitly asks to inspect or choose from the backlog, or explicitly names a backlog ticket.
- If no open non-backlog ticket remains, report that there is no immediately actionable ticket and suggest creating a regular ticket for the next planned work.
