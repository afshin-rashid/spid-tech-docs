# SPiD API Documentation

The SPiD API Documentation is a Clojure app that pulls information from the API
itself and entangles it with examples and meta-data stored in this repository.
The site can be browsed/developed live through an embedded Jetty instance, or
exported.

## Up and running

To test all the available features, you will need to put your API credentials in
resources/config.edn. Copy resources/config.default.edn and replace the
placeholders. Basic browsing should work fine, as there is a local cached copy
of the endpoint descriptions in the repo.

1. **Verify Java version**
  Run `java -version` and verify that it is version 1.7 something. If not,
  [download from Oracle]("http://docs.oracle.com/javase/7/docs/webnotes/install/index.html").

2. **Install Leiningen**
   Install [Leiningen](https://github.com/technomancy/leiningen#leiningen). If
   you use homebrew:

   ```sh
   brew update && brew install leiningen
   ```

3. Install the git submodules
   The example code as well as syntax highlighting themes are pulled in as
   submodules.

   ```sh
   git submodule update --init
   ```

4. **Run the web server**
   ```sh
   lein ring server
   ```

   That should pop up a browser with the SPiD API documentation.

## Exporting the site

If you want a static export of the site, `cd` into the root of the project, then
run:

```sh
lein build-site
```

The resulting site in `./dist` is ready for use and can be `scp`-ed directly to
a server. Note that currently, it will not work well from local disk because it
uses absolute URLs, so front-end assets won't load, and links will not work.

## Writing documentation

Documentation is stored in two main formats:

1. [Markdown](http://daringfireball.net/projects/markdown/syntax) is used for
   articles/guides as well as most blobs of text describing various aspects of
   API endpoints etc. Many
   [Github extensions](https://help.github.com/articles/github-flavored-markdown)
   are supported by [the parser](https://github.com/sirthias/pegdown).
2. [edn](https://github.com/edn-format/edn) is used for structured meta-data
   and content that is cross-linked or otherwise used across multiple places in
   the documentation. **edn** is like JSON on steroids. While JSON is
   "JavaScript Object Notation", edn is "Clojure data structure notation". It
   supports booleans, nil (null in JSON), strings, integers, floats, lists,
   vectors (arrays in JSON), maps (objects in JSON), sets, keywords (used for
   "special strings", like keys in maps, ids, etc) and more.

All documentation lives in the resources/ directory.

### resources/public

Files in this directory will be served as is. Put anything you like here. Please
do not put HTML files that duplicate markup from the main site. If you want to
create new pages, look into creating an article instead of dumping a file here.

### resources/articles

.md files in this directory will be available as web pages with a URL
corresponding to the filename. resources/articles/getting-started.md is turned
into the page /getting-started/.

These markdown files support a few custom extensions.

#### Tabs

To create tabbed content, add a header starting with the string `:tabs`. Then
add individual tabs in headers starting with the string `:tab`. End the tabbed
section with a header of the same level as you started and the text `/:tabs`. An
example visualizes how this works:

```md
## :tabs A tab container

### :tab PHP

Something about PHP in here.

### :tab Java

Something about Java in here

## /:tabs
```

Note that you may use any heading level you like, but the opening `:tabs` and
closing `/:tabs` must be in the same heading level.

#### Example code

One of the goals of the SPiD documentation is for it to be fully automated and
verifiable. This means that code examples must stem from real code-bases that
can be run and tested. All code used in the docs live in the example code git
submodule (at [resources/example-repo](./resources/example-repo)). To insert a
snippet of code from this repository into your pages, you can use the
`:example-code` custom extension.

First, the code example to be extracted must be commented. The syntax varies by
language. For Java and PHP, it looks like this:

```java
/** Fetch user information and add to session */
@RequestMapping("/create-session")
String createSession(@RequestParam String code, HttpServletRequest request) throws SPPClientException, SPPClientResponseException, SPPClientRefreshTokenException {
    SPPClient client = createUserClient(code);
    JSONObject user = client.GET("/me").getJSONObject();
    HttpSession session = request.getSession();
    session.setAttribute("user", user);
    session.setAttribute("client", client);
    return "redirect:/";
}
/**/
´´´

Start with a comment `/** ... */` and end the example with `/**/`. Then refer to
this example in the markdown page with the path to this file and the leading
comment:

```
:example-code java /sso/src/main/java/SingleSignOnController.java "Fetch user information and add to session"
```

You refer to the code snippet with the full comment instead of an ID or other
special tag because this is the best way to keep the sample code nice readable
for those who clone it directly.

### resources/endpoints

These are edn files that describe additional meta-data about endpoints. We're
hoping that many of these files will eventually be made redundant by having the
/endpoints endpoint include more details about each endpoint.

### resources/sample-responses

**NB!** These files are generated, do not edit. Eventually there will be a
script to regenerate all of them, but for now you can generate individual ones
from the REPL:

```clj
(in-ns spid-docs.sample-responses)

;; Fetches a sample response from the /api/2/logins endpoint and writes it to
;; file (also returns the sample response)
(generate-sample-response "logins")
```

### Other files in resources

In addition to these directories, there are various files that have specific
functionality.

#### resources/apis.edn

This file describes the service/api categorization. The ids of services and apis
defined in this file can be used to categorize endpoints in
resources/endpoints/*.edn.

#### resources/cached-endpoints.edn

This file is a cached response from the /endpoints endpoint. It was generated by
use of the Clojure SDK:

```clj
(require '[spid-sdk-clojure.core :refer [create-client GET]])

(let [options {:spp-base-url "https://stage.payment.schibsted.no"}
      client (create-client client-id secret options)]
  (clojure.pprint/pprint (GET client "/endpoints")))
```

#### resources/parameters.edn

This file maps parameters used across multiple endpoints to a cross-cutting API
"concept", e.g. a page available at /concepts/<concept>, e.g.
/concepts/pagination

#### resources/types.edn

Types used across many endpoints. Many types are defined inline for the
endpoints where they are used. If a type is used in more than one endpoint, this
is the place to define it, to enable cross-linking.

Types that don't have descriptions will not be linked. This is used for basic
types like strings and numbers, where additional explanation is not necessary.
