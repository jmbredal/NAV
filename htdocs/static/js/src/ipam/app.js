// MVC for prefix tree, probably forms etc.

define(function(require, exports, module) {
  var _ = require("libs/underscore");
  var Foundation = require("libs/foundation.min");
  var Backbone = require("backbone");
  var Marionette = require("marionette");

  var Models = require("src/ipam/models");
  var Views = require("src/ipam/views");

  var util = require("src/ipam/util");

  // == APP SINGLETON
  var App = new Marionette.Application();
  var debug = util.debug("app");

  // == APP LIFECYCLE MANAGEMENT

  // TODO: Create regions for forms, main statistics (overused/underused networks)
  App.on("before:start", function() {
    App.addRegions({
      main: "#prefix-tree",
      controls: "#ipam-controls"
    });
  });

  App.on("start", function() {
    debug("Trying to render prefix tree...");
    this.main.show(new Views.RootView({
      collection: new Models.PrefixNodes(),
      childView: Views.NodeView
    }));

    this.controls.show(new Views.ControlView());

    debug("Didn't crash. Great success!");

    // Must be called for Foundation to notice the generated accordions
    $(document).foundation({
      accordion: {
        multi_expand: true
      }
    });
  });

  // Handle flash messages
  App.vent.on("flash", function(klass, msg) {
    util.debug("flash")("Flashed a message");
    var template = _.template("<div class='<%= klass %>'><%= content %></div>");
    var content = template({
      klass: klass,
      content: msg
    });
    $("#ipam-flash").html(content);
  });

  App.vent.on("flash:reset", function() {
    util.debug("flash")("Reset flash");
    $("#ipam-flash").html(null);
  });

  // Debug button
  $("#mybtn").on("click", function() {
    App.vent.trigger("fetch:all");
  });

  //util.debugListen("available_subnets");
  util.debugListen("models:prefixnodes");
  util.debugListen("views");
  util.debugUnlisten("views:usagegraph");
  util.debugListen("app");

  module.exports = App;

});
