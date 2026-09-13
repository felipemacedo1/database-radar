# Database Radar

Database Radar is an offline static-analysis tool for answering a practical
legacy-maintenance question: before changing a database table or column, where
should a developer look in a Java codebase?

The project is in Phase 0 (research and design). Its core principles are:

- analyze source without compiling or executing the target project;
- preserve file and line evidence for every finding;
- distinguish reads, writes, mappings, and possible call paths;
- report confidence and uncertainty instead of presenting guesses as facts;
- require neither an LLM, a paid API, nor access to a real database.

Research, architecture decisions, the first end-to-end spike, and reproducible
usage instructions will be added incrementally.
