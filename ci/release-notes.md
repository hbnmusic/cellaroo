Sideload build of Cellaroo.

- `cellaroo-<version>.apk`: signed with your private release key. Present only once the four signing secrets are set in the repo.
- `cellaroo-<version>-preview.apk`: same code, signed with the key committed in this repo, installs as a separate app ("Cellaroo Preview").

Data does not move between the two automatically. Use Stats → Export backup in one and Restore in the other.
