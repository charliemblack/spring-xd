/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
/**
 * @author Ilayaperumal Gopinathan
 * @author Andrew Eisenberg
 * @author Gunnar Hillert
 */

/*global Backbone, d3, define, clearTimeout, setTimeout, _*/

// This file defines the backbone model used by xd
define([], function() {
	'use strict';

	return function(client, backbone, config) {

		var URL_ROOT = config.urlRoot;
		var ACCEPT_HEADER = config.acceptHeader;
		var PAGE_SIZE = config.pageSize;
		var REFRESH_INTERVAL = config.refreshInterval;

		console.log('XD core configuration:', config);

		// The XDModel is a collection of queries known by the client
		// abstract data type for all XD artifacts
		var Artifact = Backbone.Model.extend({});

		// a group of artifacts from the server.
		// has a query associated with it
		var ArtifactGroup = Backbone.Collection.extend({
			XDModel: Artifact
		});

		// to keep track of the pagination retrieved from the server
		var Query = Backbone.Model.extend({
			// expected props: kind, size, totalElements, totalPages, number, artifacts

			getUrl : function() {
				return URL_ROOT + this.get('artifact').url;
			},

			getHttpParams : function() {
				var pageNum = (this.get('number') || 0);
				return { size: PAGE_SIZE, page: pageNum };
			},

			reset : function() {
				this.unset('number');
				this.set('artifacts', []);
			}
		});

		var Model =  Backbone.Model.extend({
			addQuery : function(kind, pageInfo, items) {
				var args = pageInfo || {};
				items = items || [];
				args.artifact = this.findArtifact(kind);
				var artifacts = new ArtifactGroup();
				items.forEach(function(item) {
					artifacts.add(new Artifact(item));
				});
				args.artifacts = artifacts;
				var query = new Query(args);
				this.set(kind, query);
			}
		});

		var XDModel = new Model();

		// define the query kinds we care about
		XDModel.artifacts = [];

		function defineArtifact(kind, name, url) {
			XDModel.artifacts.push({ kind: kind, name: name, url: url });
		}

		function findArtifact(kind) {
			return _.find(XDModel.artifacts, function(artifact) {
				return artifact.kind === kind;
			});
		}

		XDModel.artifacts.forEach(function(artifact) {
			XDModel.addQuery(artifact.kind);
		});

		// Batch Jobs model
		var JobDefinition = Backbone.Model.extend({
			urlRoot: URL_ROOT + '/jobs/',
			url: function() {
				return this.urlRoot + this.id + '/jobs.json';
			},
			idAttribute: 'name',
			deploy: function() {
				var createPromise = client({
					path: URL_ROOT +  '/jobs/' + this.id,
					params: { "deploy" : 'true' } ,
					method: 'PUT',
					headers: ACCEPT_HEADER
				 });
				 return XDModel.jobDefinitions.fetch({merge:true, update:true });
			},
			undeploy: function() {
				var createPromise = client({
					path: URL_ROOT +  '/jobs/' + this.id,
					params: { "deploy" : 'false' } ,
					method: 'PUT',
					headers: ACCEPT_HEADER
				 });
				 return XDModel.jobDefinitions.fetch({merge:true, update:true });
			}
		});

		var JobDefinitions = Backbone.Collection.extend({
			model: JobDefinition,
			urlRoot: URL_ROOT + "/jobs?deployments=true",
			url: function() {
				return this.urlRoot;
			},
			comparator: 'name',
			jobs: [],
			parse: function(response) {
				this.jobs = response.content;
				return this.jobs;
			},
			startFetching: function() {
				this.stopFetch = false;
				this.fetch({change:true, add:false}).then(
					function() {
						this.fetchTimer = setTimeout(function() {
							if (!this.stopFetch) {
								this.startFetching();
							}
						}.bind(this), REFRESH_INTERVAL);
					}.bind(this));
			},

			stopFetching: function() {
				if (this.fetchTimer) {
					clearTimeout(this.fetchTimer);
				}
				this.stopFetch = true;
			}
		});

		XDModel.jobDefinitions = new JobDefinitions();

		var Execution = Backbone.Model.extend({
			urlRoot: URL_ROOT + '/batch/jobs/',
			url: function() {
				return this.urlRoot + this.id + '.json';
			},
			idAttribute: 'id',
			parse: function(response) {
				return response.jobExecution;
			},
			transform: function() {
				return {
					millis: Math.floor(Math.random() * 1000), // randomized data for now this.get('duration'),
					name: this.id,
					status: this.get('status')
				};
			}

		});
		var Executions = Backbone.Collection.extend({
			model: Execution,
			executions: [],
			urlRoot: URL_ROOT + '/batch/executions',
			url: function() {
				return this.urlRoot;
			},
			parse: function(response) {
				this.executions = response;
			},
			startFetching: function() {
				this.stopFetch = false;
				this.fetch({change:true, add:false}).then(
					function() {
						this.fetchTimer = setTimeout(function() {
							if (!this.stopFetch) {
								this.startFetching();
							}
						}.bind(this), REFRESH_INTERVAL);
					}.bind(this));
			},

			stopFetching: function() {
				if (this.fetchTimer) {
					clearTimeout(this.fetchTimer);
				}
				this.stopFetch = true;
			}
		});

		XDModel.jobExecutions = new Executions();

		var JobInstance = Backbone.Model.extend({
			urlRoot: URL_ROOT + '/batch/jobs/',
			url: function() {
				return this.urlRoot + this.get('name') + '/' + this.id + '.json';
			},
			idAttribute: 'id',
			parse: function(instance) {
				return {
					name: instance.jobName,
					id: instance.id,
					version: instance.version,
					nameId: instance.name+ '/' + instance.id,
					jobParameters: instance.jobParameters,
					jobExecutions: instance.jobExecutions ?
						new Executions(Object.keys(instance.jobExecutions).map(function(key) {
							var execution = new Execution(instance.jobExecutions[key]);
							execution.id = key;
							return execution;
						})) :
						new Executions()
				};
			},
			transformExecutions: function() {
				return this.get('jobExecutions').map(function(execution) {
					return execution.transform();
				});
			}
		});
		var JobInstances = Backbone.Collection.extend({
			instances: [],
			model: JobInstance,
			urlRoot: URL_ROOT + '/batch/jobs/',
			url: function() {
				return this.urlRoot + this.jobName + '/instances.json';
			},
			parse: function(response) {
				this.instances = response;
			}
		});

		XDModel.jobInstances = new JobInstances();

		var BatchJob = Backbone.Model.extend({
			urlRoot: URL_ROOT + '/batch/jobs',
			url: function() {
				return this.urlRoot + '/' + this.id + '.json';
			},
			idAttribute: 'name',
			parse: function(data) {
				// for some reason coming back as a string
				if (typeof data === 'string') {
					data = JSON.parse(data);
				}
				if (data.job) {
					data = data.job;
				}
				if (data.jobInstances) {
					data.jobInstances = new JobInstances(Object.keys(data.jobInstances).map(function(key) {
						var instance = new JobInstance(data.jobInstances[key]);
						instance.id = key;
						instance.set('name', this.id);
						return instance;
					}, this));
				} else {
					data.jobInstances = new JobInstances();
					data.jobInstances.jobName = data.name;
				}
				return data;
			}
		});

		var BatchJobs = Backbone.Collection.extend({
			model: BatchJob,
			urlRoot: URL_ROOT + '/batch/jobs',
			url: function() {
				return this.urlRoot ;
			},
			jobs: [],
			parse: function(data) {
				this.jobs = data;
				return data;
			},
			comparator: 'name',

			startFetching: function() {
				this.stopFetch = false;
				console.log("Start fetching for Batch Jobs...");
				this.fetch().then(
					function() {
						this.fetchTimer = setTimeout(function() {
							if (!this.stopFetch) {
								this.startFetching();
							}
						}.bind(this), REFRESH_INTERVAL);
					}.bind(this));
			},

			stopFetching: function() {
				console.log("Stop fetching for Batch Jobs...");
				if (this.fetchTimer) {
					clearTimeout(this.fetchTimer);
				}
				this.stopFetch = true;
			}
		});

		XDModel.batchJobs = new BatchJobs();

		//JobLaunch
		//Define the Model

		var DataType = Backbone.Model.extend({
			defaults: {
				id: '1',
				key: 'string',
				name: 'String',
				selected: false
			}
		});

		var DataTypes = Backbone.Collection.extend({
			model: DataType
		});

		XDModel.createDataTypes = function createDataTypes() {
			return new DataTypes( [
				new DataType({id:1, key:'string', name: 'String', selected: true}),
				new DataType({id:2, key:'date',   name: 'Date'}),
				new DataType({id:3, key:'long',   name: 'Long'}),
				new DataType({id:4, key:'double', name: 'Double'})
			] );
		}

		XDModel.dataTypes = XDModel.createDataTypes();

		var JobParameter = Backbone.Model.extend({
			idAttribute: 'key',
			defaults: {
				key: '',
				value: '',
				isIdentifying: true,
				type: new DataType()
			}
		});

		XDModel.jobParameter = new JobParameter();

		XDModel.createJobParameter = function() {
			return new JobParameter();
		}

		var JobParameters = Backbone.Collection.extend({
			model: JobParameter
		});

		XDModel.jobParameters = new JobParameters();

		XDModel.createJobParameters = function createJobParameters() {
			return new JobParameters( [
				new JobParameter( {key : "super", value : "value1" } ),
				new JobParameter( {key : "super", value : "value2" } ),
				new JobParameter( {key : "super", value : "value3" } )
			] );
		}

		var JobLaunchRequest = Backbone.Model.extend({
			urlRoot: URL_ROOT + '/batch/jobs.json',
			url: function() {
				return this.urlRoot ;
			},
			launch: function(jobName, parameters) {
				var params = "";
				if (parameters) {
					params = parameters;
				}
				var createPromise = client({
					path: URL_ROOT +  '/jobs/' + jobName + '/launch',
					params: { "jobParameters" : params } ,
					method: 'PUT',
					headers: ACCEPT_HEADER
				 });
				return XDModel.batchJobs.fetch({merge:true, update:true });
			},
			defaults: {
				jobname: 'your job name',
				jobParameters: new JobParameters(
					[new JobParameter({key : "", value : ""})])
			},

			convertToJsonAndSend: function() {
				console.log('converting to Json');
				console.log('Model Change: ' + JSON.stringify(this.toJSON()))

				var jsonData = {};
				this.get('jobParameters').forEach(function(jobParameter) {
					var key = jobParameter.get('key');
					var value = jobParameter.get('value');
					var isIdentifying = jobParameter.get('isIdentifying');
					var dataType = jobParameter.get('type');

					var dataTypeToUse = '';
					if(!(typeof dataType === "undefined")) {
						var dataTypeToUse = "(" + dataType.get('key') + ")";
					}

					if (isIdentifying) {
						jsonData["+" + key + dataTypeToUse] = value;
					}
					else {
						jsonData["-" + key + dataTypeToUse] = value;
					}
				});
				console.log(jsonData);
				var jsonDataAsString = JSON.stringify(jsonData);
				console.log(jsonDataAsString);

				this.launch(this.get('jobname'), jsonDataAsString);

			}
		});

		XDModel.jobLaunchRequest = new JobLaunchRequest();

		return XDModel;
	};
});
