/**
 * Highcharts JS theme for Bootswatch Superhero
 * 
 */

Highcharts.theme = {
  colors : [ "#2b908f", "#90ee7e", "#f45b5b", "#7798BF", "#aaeeee", "#ff0066",
      "#eeaaee", "#55BF3B", "#DF5353", "#7798BF", "#aaeeee" ],
  chart : {
    backgroundColor : '#2b3e50',
    style : {
      fontFamily : "'Lato', 'Helvetica Neue', Helvetica, Arial, sans-serif"
    },
    plotBorderColor : '#606063'
  },
  title : {
    style : {
      color : '#E0E0E3',
      textTransform : 'uppercase',
      fontSize : '20px'
    }
  },
  subtitle : {
    style : {
      color : '#E0E0E3',
      textTransform : 'uppercase'
    }
  },
  xAxis : {
    gridLineColor : '#4e5d6c',
    labels : {
      style : {
        color : '#E0E0E3'
      }
    },
    lineColor : '#4e5d6c',
    minorGridLineColor : '#4e5d6c',
    tickColor : '#4e5d6c',
    title : {
      style : {
        color : '#A0A0A3'

      }
    }
  },
  yAxis : {
    gridLineColor : '#4e5d6c',
    labels : {
      style : {
        color : '#E0E0E3'
      }
    },
    lineColor : '#4e5d6c',
    minorGridLineColor : '#4e5d6c',
    tickColor : '#4e5d6c',
    tickWidth : 1,
    title : {
      style : {
        color : '#A0A0A3'
      }
    }
  },
  tooltip : {
    backgroundColor : 'rgba(78, 93, 108, 0.9)',
    style : {
      color : '#ffffff'
    }
  },
  plotOptions : {
    series : {
      dataLabels : {
        color : '#B0B0B3'
      },
      marker : {
        lineColor : '#333'
      }
    },
    boxplot : {
      fillColor : '#4e5d6c'
    },
    candlestick : {
      lineColor : 'white'
    },
    errorbar : {
      color : 'white'
    }
  },
  legend : {
    itemStyle : {
      color : '#E0E0E3'
    },
    itemHoverStyle : {
      color : '#FFF'
    },
    itemHiddenStyle : {
      color : '#606063'
    }
  },
  credits : {
    style : {
      color : '#666'
    }
  },
  labels : {
    style : {
      color : '#4e5d6c'
    }
  },

  drilldown : {
    activeAxisLabelStyle : {
      color : '#F0F0F3'
    },
    activeDataLabelStyle : {
      color : '#F0F0F3'
    }
  },

  navigation : {
    buttonOptions : {
      symbolStroke : '#DDDDDD',
      theme : {
        fill : '#4e5d6c'
      }
    }
  },

  // scroll charts
  rangeSelector : {
    buttonTheme : {
      fill : '#4e5d6c',
      stroke : '#000000',
      style : {
        opacity : 0.5,
        color : '#ffffff'
      },
      states : {
        hover : {
          fill : '#4e5d6c',
          stroke : '#000000',
          style : {
            opacity : 1,
            color : 'white'
          }
        },
        select : {
          fill : '#4e5d6c',
          stroke : '#000000',
          style : {
            opacity : 1,
            color : 'white'
          }
        }
      }
    },
    inputBoxBorderColor : '#4e5d6c',
    inputStyle : {
      backgroundColor : '#333',
      color : '#ffffff'
    },
    labelStyle : {
      opacity: 0.5,
      color : '#ffffff'
    }
  },

  navigator : {
    handles : {
      backgroundColor : '#4e5d6c',
      borderColor : 'rgba(255,255,255,0.3)'
    },
    outlineColor : '#4e5d6c',
    maskFill : 'rgba(255,255,255,0.1)',
    series : {
      color : '#7798BF',
      lineColor : '#ffffff'
    },
    xAxis : {
      gridLineColor : '#4e5d6c'
    }
  },

  scrollbar : {
    barBackgroundColor : '#808083',
    barBorderColor : '#808083',
    buttonArrowColor : '#CCC',
    buttonBackgroundColor : '#606063',
    buttonBorderColor : '#606063',
    rifleColor : '#FFF',
    trackBackgroundColor : '#404043',
    trackBorderColor : '#404043'
  },

  // special colors for some of the
  legendBackgroundColor : 'rgba(78, 93, 108, 0.5)',
  background2 : '#4e5d6c',
  dataLabelsColor : '#B0B0B3',
  textColor : '#ffffff',
  contrastTextColor : '#F0F0F3',
  maskColor : 'rgba(255,255,255,0.3)'
};

// Apply the theme
Highcharts.setOptions(Highcharts.theme);