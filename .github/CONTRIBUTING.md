Hi reader!

If you ended here then you probably want to contribute to this project: thanks for that!

In order to have your contribution more easily integrated please respect the following rules:

## Contribution rules

1. contributions are provided by pull-request only (no email, or anything else)
1. before submitting your pull-request you have successfully run a local build using the following command
    - `mvn -Prun-its clean install`
1. if your contribution consist in some code modification then your change is covered by a unit or integration test
    - _for documentation only contribution, this requirement can be omitted)_
1. you have kept several commits in your PR only if those commits are relevant ; for typos commits or like please squash them.

## Integration warning

As we want to keep the project clean, __every commit__ in any branch (expect master & maintenance branches) can be __rewritten__.

From the last sentence we give 2 advices:

- initiate your PR from a branch in your fork and not from the master. When your PR will be integrated, it can be modified & adapted leading your fork with a master branch that as diverged from upstream
- do not initiate work in your fork from a PR that could be rewritten