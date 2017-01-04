# Sut API
The sut API is now designed as ReST over HTTP.
## /api/v1
This is an informational endpoint that describes the available resources. **Also, the following endpoints documented here are all sub-URIs of this endpoint.** E.g., /api/v1/config-profiles/, etc. Individual resources are returned in JSON format. Resultsets/Collections (i.e., lists of resources) are returned as JSON wrapped in the following meta-data document:
```
{
  "next": 2,
  "previous": null,
  "last": 5,
  "count": 25,
  "per-page": 5,
  "current": 1,
  "results": [...]
}
```
## Config Profiles
### GET `/config-profiles/`
#### Status codes
200
#### Response body
A list (possibly empty) of resources.
#### Description
A list of config profiles. Filters: `name`, `program_name`.
### GET `/config-profiles/:name`
#### Response codes
200, 404
#### Response body
An individual resource in JSON format.
#### Description
An individual config profile by name (its primary key). E.g.,
```
{
  "name": "qa-only",
  "program-name": "e2e.smoketest",
  "data": {...},
  "related": {
    "program": "http://.../api/v1/programs/e2e.smoketest",
    "test-runs": ["http://.../api/v1/test-runs/abc123"]
  }
}
```
### POST `/config-profiles/`
#### Status codes
201, 409
#### Response body
The new resource in JSON format.
#### Description
Create a new config profile. The request body is expected to JSON in the following format:
```
{
  "name": "qa-only",
  "program_name": "e2e.smoketest",
  "data": {
    "ENVIRONMENTS": [{
      "name": "qa", ...
    }]
  }
}
```
Post a new config profile.
### PUT `/config-profiles/:name`
#### Status codes
204, 404, 409
#### Response body
Empty.
#### Description
Update a config profile by name. E.g., to change the name of the config profile,
```
PUT /api/v1/config-profiles/qa-only HTTP/1.1
...
{
  "name": "qa-only.old"
}
```
### DELETE `/config-profile/:name`
#### Status codes
204, 404
#### Response body
Empty.
#### Description
Remove a config profile by name.

## Programs
### GET `/programs/`
#### Status codes
200
#### Response body
A list of available programs (test-suites).
### GET `/programs/:name`
#### Status codes
200, 404
#### Response body
An individual program by name (its primary key).
### GET `/programs/:name/config-profiles/`
#### Status codes
200, 404
#### Response body
A list of config profiles associated with a named program. This is functionally equivalent to `/config-profiles/?program_name=<name>`.
### POST `/programs/`
#### Status codes
201, 409
#### Response body
JSON formatted resource.
#### Description
Creates a new program. The request body is expected to be JSON in the following form:
```
{
   "name":"foo",
   "data": {
     "main": [{"module": "http://..."}]
   }
}
```
## /modules/
## /actions/
## /test-runs/
### /test-runs/:id/checkpoints/
### /test-runs/:id/log-entries/

